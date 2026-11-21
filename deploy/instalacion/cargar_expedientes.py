#!/usr/bin/env python3
"""Carga los expedientes ficticios (datos/expedientes.json) por la API del administrador, después de cargar.py.

Módulo para dos llamadores: la pila del archivo (CLI de abajo) y datos_prueba.py. Los dos tienen cargada una parte
del archivo, así que solo se cargan los expedientes de los casos cuyo documento está cargado y listo (READY).
`cliente.admin(metodo, ruta, cuerpo=None, binario=None)` devuelve el JSON de la respuesta, como Api.admin de datos_prueba.py.

Los documentos subidos se leen de forma asíncrona: antes de marcar una sustitución se espera a que los dos estén READY
(nunca se espera a imágenes: no se sustituyen). Un caso que no es caso (caseDocument) se omite con «no es caso», salvo con
marcar_casos=True (CLI: --marcar-casos), que lo marca con case-marks; la pila del archivo lo necesita porque cargar.py
inserta por SQL sin marcar casos.

Uso: python3 pila/cargar_expedientes.py [--mapa RUTA] [--marcar-casos] [--espera SEGUNDOS]
(mapa por defecto estado/carga.json; admite también mapa-ids.local.json)"""
import argparse
import json
import mimetypes
import os
import sys
import tempfile
import time
import urllib.parse
import urllib.request
import uuid
from types import SimpleNamespace
from pathlib import Path

EXP = Path(__file__).resolve().parents[2] / "datos-prueba" / "archivo-ficticio"
sys.path.insert(0, str(Path(__file__).resolve().parent))
MOTIVO_SIN_CASO = "el documento del caso no está cargado"


def guardar_json(ruta, datos):
    """Escribe `datos` en `ruta` de forma atómica (temporal de la misma carpeta + os.replace) y con modo 0o600."""
    ruta = Path(ruta)
    texto = json.dumps(datos, ensure_ascii=False, indent=1)  # si falla, no se toca el fichero ni se crea el temporal
    fd, tmp = tempfile.mkstemp(dir=ruta.parent, prefix=ruta.name + ".")  # mkstemp crea con 0o600
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(texto)
        os.replace(tmp, ruta)
    except BaseException:
        os.unlink(tmp)
        raise


def multipart(nombre, contenido, tipo="application/octet-stream"):
    limite = uuid.uuid4().hex
    cabecera = (f'--{limite}\r\nContent-Disposition: form-data; name="file"; filename="{nombre}"\r\n'
                f"Content-Type: {tipo}\r\n\r\n").encode()
    return cabecera + contenido + f"\r\n--{limite}--\r\n".encode(), f"multipart/form-data; boundary={limite}"


def titulo_de(nombre):
    s = nombre.rsplit(".", 1)[0].replace("-", " ")
    return s[0].upper() + s[1:]


def subir(cliente, empresa, fichero, proyecto):
    tipo = mimetypes.guess_type(fichero.name)[0] or "application/octet-stream"
    consulta = {"title": titulo_de(fichero.name)} | ({"projectId": proyecto} if proyecto else {})
    ruta = f"/v1/admin/companies/{empresa}/documents?{urllib.parse.urlencode(consulta)}"
    return cliente.admin("POST", ruta, binario=multipart(fichero.name, fichero.read_bytes(), tipo))["document"]["id"]


def proyecto_de(cliente, mapa_ids, prefijo):
    """Id del proyecto de la carpeta `empresa/proyecto/`, leído de cualquier documento cargado en ella; None si no hay ninguno."""
    ruta = next((r for r in mapa_ids if r.startswith(prefijo)), None)
    return cliente.admin("GET", f"/v1/admin/documents/{mapa_ids[ruta]}").get("projectId") if ruta else None


def rutas_de(e):
    """Todas las rutas que carga un expediente: su caso, sus documentos, los compartidos y los señuelos."""
    return {e["caso"], *e["documentos"], *e["compartidos"], *e["ajenos"]}


def asegurar(cfg, ruta, empresa, proyecto):
    if ruta not in cfg.mapa:
        cfg.mapa[ruta] = subir(cfg.cliente, empresa, cfg.raiz / "expedientes" / ruta, proyecto)
        cfg.cuentas["subidos"] += 1
    return cfg.mapa[ruta]


def esperar_listos(cfg, ids):
    """Espera a que todos estén READY (la lectura es asíncrona). False si alguno falla o se agota el tiempo."""
    fin = time.monotonic() + cfg.espera
    while True:
        estados = [cfg.cliente.admin("GET", f"/v1/admin/documents/{i}")["status"] for i in ids]
        if all(e == "READY" for e in estados):
            return True
        if "FAILED" in estados or time.monotonic() >= fin:
            return False
        time.sleep(cfg.intervalo)


def sustituir(cfg, viejo, nuevo):
    """Marca `viejo` como sustituido por `nuevo`, cuando los dos están listos y no estaba ya marcado."""
    v, n = cfg.mapa[viejo], cfg.mapa[nuevo]
    if ((cfg.cliente.admin("GET", f"/v1/admin/documents/{v}").get("supersededBy") or {}).get("id")) == n:
        return
    if not esperar_listos(cfg, [v, n]):
        cfg.cuentas["pendientes"].append({"viejo": viejo, "nuevo": nuevo, "motivo": "no llegaron a READY"})
        return
    cfg.cliente.admin("PUT", f"/v1/admin/documents/{v}/superseded-by", {"replacementId": n})
    cfg.cuentas["sustituidos"] += 1


def subir_ajenos(cfg, e, empresa):
    """Señuelos: se suben a su proyecto, si está cargado, y no se asignan."""
    for r in e["ajenos"]:
        p = proyecto_de(cfg.cliente, cfg.mapa, "/".join(r.split("/")[:2]) + "/")
        if p:
            asegurar(cfg, r, empresa, p)


def cargar_caso(cfg, e):
    """None si se cargó; si no, el motivo de omitirlo."""
    caso = cfg.cliente.admin("GET", f"/v1/admin/documents/{cfg.mapa[e['caso']]}")
    empresa, proyecto = caso["companyId"], caso.get("projectId")
    if caso["status"] != "READY":
        return f"el documento del caso está en {caso['status']}, no READY"
    if not caso.get("caseDocument"):
        if not cfg.marcar_casos:
            return "no es caso"
        cfg.cliente.admin("POST", f"/v1/admin/companies/{empresa}/documents/case-marks",
                          {"documentIds": [caso["id"]], "caseDocument": True})
        cfg.cuentas["marcados"] += 1
    ids = [asegurar(cfg, r, empresa, None if "/_general/" in r else proyecto) for r in e["documentos"] + e["compartidos"]]
    subir_ajenos(cfg, e, empresa)
    cfg.cuentas["añadidos"] += cfg.cliente.admin(
        "POST", f"/v1/admin/companies/{empresa}/cases/{caso['id']}/file/documents", {"documentIds": ids})["added"]
    for viejo, nuevo in e.get("sustituciones", []):
        sustituir(cfg, viejo, nuevo)
    return None


def cargar_expedientes(cliente, mapa_ids, manifiesto_expedientes, raiz_datos, marcar_casos=False, espera=180, intervalo=1.0, guardar=None):
    """Sube los documentos de cada expediente cuyo caso está cargado y los asigna a su caso. Completa `mapa_ids` con lo subido
    y llama a `guardar(mapa_ids)` tras cada caso. Un fallo en un caso se anota en `errores` y sigue con el siguiente.
    Devuelve {subidos, añadidos, omitidos, marcados, sustituidos, omitidos_casos: [{caso, motivo}], errores: [{caso, error}],
    pendientes: [{viejo, nuevo, motivo}]} (pendientes: sustituciones que no llegaron a hacerse por no estar listos)."""
    cuentas = {"subidos": 0, "añadidos": 0, "omitidos": 0, "marcados": 0, "sustituidos": 0, "omitidos_casos": [], "errores": [], "pendientes": []}
    cfg = SimpleNamespace(cliente=cliente, mapa=mapa_ids, raiz=Path(raiz_datos), cuentas=cuentas, marcar_casos=marcar_casos,
                          espera=espera, intervalo=intervalo)
    for e in manifiesto_expedientes:
        try:
            motivo = cargar_caso(cfg, e) if e["caso"] in mapa_ids else MOTIVO_SIN_CASO
        except (Exception, SystemExit) as error:
            cuentas["errores"].append({"caso": e["caso"], "error": str(error)[:300]})
            motivo = None
        if motivo:
            cuentas["omitidos"] += 1
            cuentas["omitidos_casos"].append({"caso": e["caso"], "motivo": motivo})
        if guardar:
            guardar(mapa_ids)
    return cuentas


class ClientePila:
    """Adaptador de cargar.py (sesión del administrador por cookie) a `admin(...)`."""
    def __init__(self, cargar, env):
        self.http, self.api = cargar.http, cargar.API
        r, _ = cargar.http("POST", "/v1/admin/session", {"email": env["ODIN_ADMIN_EMAIL"], "password": env["ODIN_ADMIN_PASSWORD"]})
        self.cookie = r.headers["Set-Cookie"].split(";")[0]

    def admin(self, metodo, ruta, cuerpo=None, binario=None):
        if binario is None:
            return self.http(metodo, ruta, cuerpo, cookie=self.cookie)[1]
        req = urllib.request.Request(self.api + ruta, data=binario[0], method=metodo,
                                     headers={"Content-Type": binario[1], "Cookie": self.cookie})
        with urllib.request.urlopen(req, timeout=120) as r:
            return json.loads(r.read())


def main():
    import cargar
    from propietario import proteger_medicion
    env = cargar.leer_env()
    proteger_medicion(env, cargar.API, cargar.MCP)  # D-099: antes de leer nada más ni de escribir
    ap = argparse.ArgumentParser()
    ap.add_argument("--mapa", default=str(EXP / "estado" / "carga.json"))
    ap.add_argument("--datos", default=str(EXP / "datos"))
    ap.add_argument("--marcar-casos", action="store_true")
    ap.add_argument("--espera", type=int, default=180)
    a = ap.parse_args()
    ruta = Path(a.mapa)
    datos = json.loads(ruta.read_text(encoding="utf-8"))
    carga = "documentos" in datos  # carga.json: id -> ruta; mapa-ids: ruta -> id
    mapa = {r: i for i, r in datos["documentos"].items()} if carga else datos

    def guardar(m):
        guardar_json(ruta, datos | ({"documentos": {i: r for r, i in m.items()}} if carga else m))

    exps = json.loads((Path(a.datos) / "expedientes.json").read_text(encoding="utf-8"))
    r = cargar_expedientes(ClientePila(cargar, env), mapa, exps, Path(a.datos), a.marcar_casos, a.espera, guardar=guardar)
    print(json.dumps(r, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
