#!/usr/bin/env python3
"""Primera carga: sube la carpeta del archivo de una empresa a Odín por la API (vía de la orden).
La lanza el administrador de la empresa en el servidor de la oficina: make primera-carga CARPETA=/ruta [ARGS=--si].
Pide su correo y su contraseña (getpass); la contraseña no se guarda ni se muestra. Nada sale del equipo.
Variables: INSTALACION_ENV (por defecto deploy/.env) y PRIMERA_CARGA_API_URL (por defecto el puerto de ODIN_API_PORT en ese fichero)."""
import argparse
import getpass
import hashlib
import os
import shutil
import sys
import urllib.parse
from pathlib import Path

import datos_prueba as dp

TANDA = 50  # huellas por pregunta
LIMITE = 52428800  # el de ODIN_UPLOAD_MAX_BYTES por defecto; main lo lee de deploy/.env
BASURA = {".DS_Store", "Thumbs.db", "desktop.ini"}
FACTOR_ESPACIO = 3  # el original, sus páginas leídas y los índices


def recorrer(carpeta, limite=LIMITE):
    """Los ficheros subibles como (ruta absoluta, ruta relativa) y los saltados como (ruta, motivo): vacíos, por
    tamaño o con un nombre que no se puede mandar. Sin enlaces simbólicos, ocultos (.algo) ni basura del sistema."""
    ficheros, saltados = [], []
    for raiz, dirs, nombres in os.walk(carpeta):
        dirs[:] = sorted(d for d in dirs if not d.startswith(".") and not (Path(raiz) / d).is_symlink())
        for nombre in sorted(nombres):
            f = Path(raiz) / nombre
            if nombre.startswith(".") or nombre in BASURA:
                continue
            ruta = f.relative_to(carpeta).as_posix()
            motivo = motivo_del_nombre(ruta)
            if motivo is None and (f.is_symlink() or not f.is_file()):
                continue
            motivo = motivo or motivo_del_tamano(f.stat().st_size, limite)
            if motivo:
                saltados.append((ruta, motivo))
            else:
                ficheros.append((f, ruta))
    return sorted(ficheros, key=lambda x: x[1]), saltados


def motivo_del_nombre(ruta):
    """Un salto de línea rompería los campos de la petición y un nombre que no es UTF-8 no se puede mandar."""
    if "\r" in ruta or "\n" in ruta:
        return "el nombre tiene un salto de línea"
    try:
        ruta.encode("utf-8")
    except UnicodeEncodeError:
        return "el nombre no está en UTF-8"
    return None


def motivo_del_tamano(tamano, limite):
    if tamano == 0:
        return "está vacío"
    return "supera el límite de subida" if tamano > limite else None


def mostrar(ruta):
    """La ruta escribible en la terminal aunque no sea UTF-8 o lleve saltos de línea."""
    return ruta.encode("utf-8", "backslashreplace").decode("utf-8").replace("\r", "\\r").replace("\n", "\\n")


def huella(fichero):
    h = hashlib.sha256()
    with open(fichero, "rb") as f:
        for trozo in iter(lambda: f.read(1 << 20), b""):
            h.update(trozo)
    return h.hexdigest()


def ya_estan(api, base, huellas):
    """Las huellas que la empresa o el lote ya tienen, preguntadas por tandas."""
    hechas = set()
    for i in range(0, len(huellas), TANDA):
        consulta = urllib.parse.urlencode({"sha256": huellas[i:i + TANDA]}, doseq=True)
        hechas |= set(api.admin("GET", f"{base}/files?{consulta}")["sha256"])
    return hechas


def empresa_de(api, codigo):
    empresas = api.admin("GET", "/v1/admin/companies")
    elegidas = [e for e in empresas if codigo in (None, e["code"])]
    if len(elegidas) != 1:
        raise SystemExit("No sé a qué empresa subir el archivo: " + (
            "no hay ninguna con ese código." if elegidas == [] and codigo else
            "tu usuario ve varias; repite la orden con --empresa <código>: " + ", ".join(e["code"] for e in empresas)))
    return elegidas[0]


def abrir_lote(api, base):
    """Reutiliza el lote abierto; si hay una primera carga ya en lectura o en revisión, no abre otra."""
    lotes = api.admin("GET", base)["batches"]
    if any(lote["state"] != "OPEN" for lote in lotes):
        raise SystemExit("Ya hay una primera carga en curso (leyéndose o esperando tu revisión). "
                         "Termínala en el panel, en «Primera carga». No se ha subido nada.")
    abiertos = [lote for lote in lotes if lote["state"] == "OPEN"]
    if abiertos:
        return abiertos[0]["id"], True
    return api.admin("POST", base, {"channel": "COMMAND"})["id"], False


def avisos_de_partida(api, empresa, lote, tamano, libre):
    avisos = [a["message"] for a in api.admin("GET", f"/v1/admin/companies/{empresa}/intake-batches/{lote}")["warnings"]]
    if libre < tamano * FACTOR_ESPACIO:
        avisos.append(f"Poco espacio libre en el disco del servidor ({libre // 2**20} MB) para una carpeta de "
                      f"{tamano // 2**20} MB: haz sitio antes de seguir.")
    return avisos


def pedir_sesion(api, correo=None):
    correo = correo or input("Tu correo de administrador: ").strip()
    try:
        dp.entrar(api, {"email": correo, "password": getpass.getpass("Tu contraseña (no se ve al escribirla): ")})
    except RuntimeError:
        raise SystemExit("El correo o la contraseña no son válidos. No se ha subido nada.")


def resumen(empresa, ficheros, saltados, avisos, reutiliza):
    lineas = [f"Empresa: {empresa['name']}",
              f"Ficheros: {len(ficheros)} ({sum(f.stat().st_size for f, _ in ficheros) // 2**20} MB)"]
    if reutiliza:
        lineas.append("Hay una primera carga abierta sin cerrar: se sigue con ella.")
    lineas += [f"Se salta: {mostrar(ruta)}: {motivo}" for ruta, motivo in saltados]
    return lineas + [f"AVISO: {a}" for a in avisos]


SE_SALTA = (400, 413, 415)  # lo que Odín no admite de un fichero (vacío, demasiado grande, formato): se salta y sigue


def subir(api, base, ficheros):
    """Sube de uno en uno los que faltan. Devuelve los recuentos por resultado y los que Odín no admitió."""
    cuentas, saltados = {"CREATED": 0, "ALREADY_THERE": 0, "DISCARDED_IN_BATCH": 0}, []
    for f, ruta in ficheros:
        cuerpo = dp.multipart(f.name.replace('"', "'"), f.read_bytes(), campos={"path": ruta})
        respuesta, datos = api.pedir("POST", f"{base}/files", binario=cuerpo, esperado=(200, 201) + SE_SALTA)
        if respuesta.status in SE_SALTA:
            saltados.append((ruta, (datos or {}).get("detail") or f"HTTP {respuesta.status}"))
            print(f"Se salta: {mostrar(ruta)}: {saltados[-1][1]}")
        else:
            cuentas[datos["outcome"]] += 1
    return cuentas, saltados


def leer_argumentos(argv):
    p = argparse.ArgumentParser(prog="primera-carga")
    p.add_argument("carpeta", type=Path)
    p.add_argument("--empresa", help="código de la empresa, si tu usuario ve varias")
    p.add_argument("--si", action="store_true", help="no preguntar «¿Seguir?»")
    p.add_argument("--correo")
    a = p.parse_args(argv)
    if not a.carpeta.is_dir():
        raise SystemExit(f"No existe la carpeta {a.carpeta}. No se ha subido nada.")
    return a


def presentar(api, a, libre, limite):
    """Entra, elige empresa y lote, y enseña el resumen. Devuelve (ficheros, saltados, base del lote)."""
    ficheros, saltados = recorrer(a.carpeta, limite)
    pedir_sesion(api, a.correo)
    empresa = empresa_de(api, a.empresa)
    base = f"/v1/admin/companies/{empresa['id']}/intake-batches"
    lote, reutiliza = abrir_lote(api, base)
    tamano = sum(f.stat().st_size for f, _ in ficheros)
    libre = shutil.disk_usage(a.carpeta).free if libre is None else libre
    avisos = avisos_de_partida(api, empresa["id"], lote, tamano, libre)
    print("\n".join(resumen(empresa, ficheros, saltados, avisos, reutiliza)))
    return ficheros, saltados, f"{base}/{lote}"


def ejecutar(api, argv, libre=None, limite=LIMITE):
    """Devuelve 0 si ha subido y cerrado la subida (o no había nada nuevo), 1 si la persona dijo que no."""
    a = leer_argumentos(argv)
    ficheros, saltados, lote = presentar(api, a, libre, limite)
    if not a.si and input("¿Seguir? (s/n) ").strip().lower() not in ("s", "si", "sí"):
        print("No se ha subido nada.")
        return 1
    huellas = {ruta: huella(f) for f, ruta in ficheros}
    presentes = ya_estan(api, lote, list(huellas.values()))
    cuentas, rechazados = subir(api, lote, [(f, r) for f, r in ficheros if huellas[r] not in presentes])
    print(f"Subidos: {cuentas['CREATED']}. Ya estaban: {cuentas['ALREADY_THERE'] + len(presentes)}. "
          f"Descartados en el lote: {cuentas['DISCARDED_IN_BATCH']}.")
    if saltados + rechazados:
        print(f"Saltados ({len(saltados + rechazados)}):")
        print("\n".join(f"Se salta: {mostrar(ruta)}: {motivo}" for ruta, motivo in saltados + rechazados))
    if api.admin("GET", lote)["uploaded"] == 0:
        # un lote sin ningún documento no se cierra: se queda abierto y la próxima vez se reutiliza
        print("No hay nada nuevo que subir.")
        return 0
    api.admin("POST", f"{lote}/close-upload")
    print("Siguiente paso: abre el panel de Odín y entra en «Primera carga».")
    return 0


def main():
    entorno = dp.leer_entorno(dp.PRINCIPAL / os.environ.get("INSTALACION_ENV", "deploy/.env"))
    try:
        api = dp.conectar(entorno, os.environ.get("PRIMERA_CARGA_API_URL"))
        return ejecutar(api, sys.argv[1:], limite=int(entorno.get("ODIN_UPLOAD_MAX_BYTES", LIMITE)))
    except KeyboardInterrupt:
        print("\nInterrumpida. Vuelve a lanzar la misma orden: sigue donde se quedó.", file=sys.stderr)
    except (RuntimeError, OSError) as error:
        print(f"Ha fallado algo y la carga se ha parado: {error}\n"
              "Vuelve a lanzar la misma orden: sigue donde se quedó. Si se repite, avisa a quien instaló Odín.",
              file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
