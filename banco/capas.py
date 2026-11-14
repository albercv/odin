"""Capas de archivo y baterías de preguntas del banco. Varias capas se fusionan en una carpeta de datos con el mismo
formato que la de una sola; con «ficticio» solo, todo sigue como antes. No importa nada de `banco`: config.py lo usa."""

import argparse
import json
import os
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from banco.layout import default_layout  # noqa: E402

LIST_KEYS = ("empresas", "proyectos", "trabajadores", "documentos", "fichas")
UNIQUE_KEYS = {"trabajadores": "clave", "documentos": "ruta", "fichas": "id", "claves": "id"}


class ArchivoError(ValueError):
    """Las capas o las baterías no se pueden usar tal como están."""


def _exp_datos(layout):
    return layout.exp / "datos"


def _generalidad_datos(layout):
    return layout.banco / "archivos" / "generalidad" / "datos"


def _mirador_dir(_layout):
    """Capa de Mirador: la carpeta que da ODIN_BANCO_MIRADOR (la produce otra rama; aquí solo se lee)."""
    folder = os.environ.get("ODIN_BANCO_MIRADOR")
    if not folder:
        raise ArchivoError("falta ODIN_BANCO_MIRADOR: ruta absoluta a la carpeta de Mirador (manifiesto.json, textos.json…)")
    if not Path(folder).is_dir():
        raise ArchivoError(f"no existe la carpeta de ODIN_BANCO_MIRADOR: {folder}")
    return Path(folder)


ARCHIVES = {"ficticio": _exp_datos, "generalidad": _generalidad_datos, "mirador": _mirador_dir}


@dataclass(frozen=True)
class Battery:
    archive: str  # capa donde está el fichero
    file: str
    requires: tuple[str, ...]  # capas que sus preguntas necesitan cargadas en la pila


BATTERIES = {
    "desarrollo": Battery("ficticio", "bateria.json", ("ficticio",)),
    "conector": Battery("ficticio", "bateria-conector.json", ("ficticio",)),
    "generalidad": Battery("generalidad", "bateria.json", ("ficticio", "generalidad")),
    "generalidad-expediente": Battery("generalidad", "bateria-expediente.json", ("ficticio", "generalidad")),
    "mirador": Battery("mirador", "bateria-mirador.json", ("ficticio", "mirador")),
}


def archive_dir(layout, name):
    if name not in ARCHIVES:
        raise ArchivoError(f"capa no disponible todavía: {name}")
    return ARCHIVES[name](layout)


def battery_file(layout, name):
    battery = BATTERIES[name]
    return archive_dir(layout, battery.archive) / battery.file


def missing_archives(name, archives):
    return [a for a in BATTERIES[name].requires if a not in archives]


def merged_dir(layout, archives):
    return layout.state_dir / ("archivo-" + "+".join(archives))


def data_dir(layout, archives):
    """Carpeta de datos de estas capas: la del ficticio tal cual; con varias, la fusión en el estado (si falta, se hace)."""
    archives = tuple(archives)
    if archives == ("ficticio",):
        return archive_dir(layout, "ficticio")
    out = merged_dir(layout, archives)
    if not (out / "manifiesto.json").is_file():
        merge([archive_dir(layout, name) for name in archives], out)
    return out


def _read_json(path):
    if not path.is_file():
        raise ArchivoError(f"falta {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")


def _check_unique(rows, field, section):
    seen = set()
    for row in rows:
        if row[field] in seen:
            raise ArchivoError(f"clave repetida en {section}: {row[field]}")
        seen.add(row[field])


def _copy_tree(source, target, label):
    for path in sorted(Path(source).rglob("*")):
        if path.is_file():
            dest = target / path.relative_to(source)
            if dest.exists():
                raise ArchivoError(f"fichero repetido en {label}: {dest.relative_to(target)}")
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, dest)


def _same_companies(rows):
    """Una empresa puede venir de dos capas si es idéntica (misma clave, nombre y código); si cambia algo, se niega."""
    firsts = {}
    for row in rows:
        previous = firsts.setdefault(row["clave"], row)
        if (previous["nombre"], previous["codigo"]) != (row["nombre"], row["codigo"]):
            raise ArchivoError(f"empresa repetida con datos distintos: {row['clave']}")
    return list(firsts.values())


def _copy_corpus(source, target):
    if not Path(source).is_dir():
        raise ArchivoError(f"falta el corpus: {source}")
    _copy_tree(source, target, "el corpus")


def _build_expedientes(data_dirs, target):
    for d in data_dirs:
        if (Path(d) / "expedientes").is_dir():
            _copy_tree(Path(d) / "expedientes", target / "expedientes", "los expedientes")
    files = [Path(d) / "expedientes.json" for d in data_dirs if (Path(d) / "expedientes.json").is_file()]
    if not files:
        return
    cases = [case for f in files for case in _read_json(f)]
    _check_unique(cases, "caso", "expedientes")
    _check_expedientes(cases, target)
    _write_json(target / "expedientes.json", cases)


def _files_under(root):
    return {path.relative_to(root).as_posix() for path in Path(root).rglob("*") if path.is_file()}


def _check_expedientes(cases, target):
    """Cada fichero listado existe en expedientes/, y ninguna ruta de expedientes/ existe también en el corpus."""
    for case in cases:
        for ruta in case["documentos"] + case["compartidos"] + case["ajenos"]:
            if not (target / "expedientes" / ruta).is_file():
                raise ArchivoError(f"falta en los expedientes: {ruta}")
    repeated = sorted(_files_under(target / "expedientes") & _files_under(target / "corpus"))
    if repeated:
        raise ArchivoError(f"ruta repetida en el corpus y en los expedientes: {repeated[0]}")


def _copy_layer_corpus(layer, target):
    """Una capa sin corpus/ vale si trae textos.json: sus páginas bastan para puntuar (Mirador)."""
    if not (layer / "corpus").is_dir() and (layer / "textos.json").is_file():
        return
    _copy_corpus(layer / "corpus", target)


def _merge_texts(data_dirs, target):
    """Unión de los textos.json de las capas (páginas por ruta); una ruta no puede venir de dos capas."""
    texts = {}
    for d in data_dirs:
        if (Path(d) / "textos.json").is_file():
            for route, pages in _read_json(Path(d) / "textos.json").items():
                if route in texts:
                    raise ArchivoError(f"ruta repetida en los textos: {route}")
                texts[route] = pages
    if texts:
        _write_json(target / "textos.json", texts)
    return texts


def _check_corpus(documents, corpus, texts):
    for doc in documents:
        if not (corpus / doc["ruta"]).is_file() and doc["ruta"] not in texts:
            raise ArchivoError(f"falta en el corpus: {doc['ruta']}")


def _build(data_dirs, target):
    manifests = [_read_json(Path(d) / "manifiesto.json") for d in data_dirs]
    rows = {key: [row for m in manifests for row in m.get(key, [])] for key in LIST_KEYS}
    rows["empresas"] = _same_companies(rows["empresas"])
    rows["claves"] = [row for d in data_dirs for row in _read_json(Path(d) / "claves.json")]
    for section, field in UNIQUE_KEYS.items():
        _check_unique(rows[section], field, section)
    (target / "corpus").mkdir(parents=True)
    _write_json(target / "manifiesto.json", {"semilla": manifests[0]["semilla"], **{k: rows[k] for k in LIST_KEYS}})
    _write_json(target / "claves.json", rows["claves"])
    for d in data_dirs:
        _copy_layer_corpus(Path(d), target / "corpus")
    texts = _merge_texts(data_dirs, target)
    _check_corpus(rows["documentos"], target / "corpus", texts)
    _build_expedientes(data_dirs, target)


def merge(data_dirs, out_dir):
    """Escribe out_dir (desde cero) con la unión de las carpetas de datos. Se construye al lado y se cambia de
    nombre al final: una fusión interrumpida no deja una carpeta a medias."""
    out_dir = Path(out_dir)
    staging = out_dir.with_name(out_dir.name + ".nueva")
    if staging.exists():
        shutil.rmtree(staging)
    try:
        _build(data_dirs, staging)
    except BaseException:
        shutil.rmtree(staging, ignore_errors=True)
        raise
    if out_dir.exists():
        shutil.rmtree(out_dir)
    os.replace(staging, out_dir)
    return out_dir


def load_items(layout, batteries):
    """Preguntas de todas las baterías, en el orden dado; un id repetido entre ficheros se niega."""
    items = []
    for name in batteries:
        path = battery_file(layout, name)
        if not path.is_file():
            raise ArchivoError(f"falta el fichero de la batería «{name}»: {path.name} no existe en {path.parent}")
        items += json.loads(path.read_text(encoding="utf-8"))
    _check_unique(items, "id", "preguntas")
    return items


def main(argv=None, layout=None):
    ap = argparse.ArgumentParser(prog="archivos")
    orders = ap.add_subparsers(dest="orden", required=True)
    orders.add_parser("fusionar").add_argument("--archivos", required=True, help="capas separadas por comas")
    args = ap.parse_args(argv)
    layout = layout or default_layout()
    names = args.archivos.split(",")
    try:
        out = merge([archive_dir(layout, name) for name in names], merged_dir(layout, names))
    except ArchivoError as error:
        print(f"error de archivos: {error}", file=sys.stderr)
        return 1
    print(out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
