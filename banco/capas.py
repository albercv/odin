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
UNIQUE_KEYS = {"empresas": "clave", "trabajadores": "clave", "documentos": "ruta", "fichas": "id", "claves": "id"}


class ArchivoError(ValueError):
    """Las capas o las baterías no se pueden usar tal como están."""


def _exp_datos(layout):
    return layout.exp / "datos"


def _generalidad_datos(layout):
    return layout.banco / "archivos" / "generalidad" / "datos"


ARCHIVES = {"ficticio": _exp_datos, "generalidad": _generalidad_datos}


@dataclass(frozen=True)
class Battery:
    archive: str  # capa donde está el fichero
    file: str
    requires: tuple[str, ...]  # capas que sus preguntas necesitan cargadas en la pila


BATTERIES = {
    "desarrollo": Battery("ficticio", "bateria.json", ("ficticio",)),
    "conector": Battery("ficticio", "bateria-conector.json", ("ficticio",)),
    "generalidad": Battery("generalidad", "bateria.json", ("ficticio", "generalidad")),
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


def _copy_corpus(source, target):
    source = Path(source)
    if not source.is_dir():
        raise ArchivoError(f"falta el corpus: {source}")
    for path in sorted(source.rglob("*")):
        if path.is_file():
            dest = target / path.relative_to(source)
            if dest.exists():
                raise ArchivoError(f"fichero repetido en el corpus: {dest.relative_to(target)}")
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, dest)


def _check_corpus(documents, corpus):
    for doc in documents:
        if not (corpus / doc["ruta"]).is_file():
            raise ArchivoError(f"falta en el corpus: {doc['ruta']}")


def _build(data_dirs, target):
    manifests = [_read_json(Path(d) / "manifiesto.json") for d in data_dirs]
    rows = {key: [row for m in manifests for row in m.get(key, [])] for key in LIST_KEYS}
    rows["claves"] = [row for d in data_dirs for row in _read_json(Path(d) / "claves.json")]
    for section, field in UNIQUE_KEYS.items():
        _check_unique(rows[section], field, section)
    (target / "corpus").mkdir(parents=True)
    _write_json(target / "manifiesto.json", {"semilla": manifests[0]["semilla"], **{k: rows[k] for k in LIST_KEYS}})
    _write_json(target / "claves.json", rows["claves"])
    for d in data_dirs:
        _copy_corpus(Path(d) / "corpus", target / "corpus")
    _check_corpus(rows["documentos"], target / "corpus")


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
