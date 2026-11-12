#!/usr/bin/env python3
"""Carga datos ficticios en una instalación vacía de Odín, todo por la API, para probarla desde una IA de escritorio.
Sale del archivo ficticio de la medición. Las credenciales van a data/datos-prueba/ (permisos 600), nunca a pantalla.
Si algo falla después del primer arranque, vuelve a dejar la instalación vacía (deploy/instalacion/reiniciar.sh).
Variables: INSTALACION_ENV (por defecto deploy/.env) e INSTALACION_PROYECTO (por defecto odin)."""
import json
import os
import re
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from collections import namedtuple
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
EXP = RAIZ / "experimentos" / "2026-11-12-archivo-ficticio"
ARCHIVO = EXP / "datos"
TEMAS = RAIZ / "api/odin-adapter-tagger-ollama/src/test/resources/tagging-battery/temas.json"
ESCANEO = RAIZ / "deploy" / "demo" / "escaneo-ficticio.png"



def copia_principal():
    """La copia principal del repositorio, la que tiene deploy/.env y data/, aunque se lance desde una worktree."""
    try:
        comun = subprocess.run(["git", "-C", str(RAIZ), "rev-parse", "--path-format=absolute", "--git-common-dir"],
                               capture_output=True, text=True, check=True).stdout.strip()
        return Path(comun).parent
    except (OSError, subprocess.CalledProcessError):
        return RAIZ


PRINCIPAL = copia_principal()
CREDENCIALES = PRINCIPAL / "data" / "datos-prueba"
sys.path.insert(0, str(EXP / "pila"))
import sustituciones  # noqa: E402

COMUNIDADES = {
    "ribera": ["c1-olmo14", "c2-almendros", "c3-puerto3", "c1-calle-abeto-16", "c1-calle-pino-26",
               "c1-calle-roble-46", "c2-los-naranjos-2", "c2-las-encinas-2"],
    "albar": ["c3-avenida-del-muelle-33", "c3-avenida-del-puerto-14"],
}
ABIERTO = {"ribera": "procedimientos-generales"}
# clave, trabajador del archivo del que salen el nombre y la empresa, proyectos ("*": todos los de su empresa)
TRABAJADORES = [("marta", "t01", ["*"]), ("javier", "t02", ["c1-calle-pino-26", "c1-calle-roble-46"]),
                ("ignacio", "t06", ["*"])]
# Temas de la lista de fincas que el administrador de prueba pone a cada tipo de caso (origen ADMIN).
TEMAS_DE_CASO = {
    "filtracion-azotea": ["agua y humedades"], "filtracion-cubierta": ["agua y humedades"],
    "bajante-patio": ["agua y humedades"], "condensaciones-4a": ["agua y humedades"],
    "capilaridad-contadores": ["agua y humedades"], "colector-garaje": ["agua y humedades"],
    "fuga-riego": ["agua y humedades"], "vaso-piscina": ["agua y humedades", "instalaciones"],
    "ruido-ascensor": ["ruido", "instalaciones"], "ruido-piscina": ["ruido", "convivencia"],
    "humos-local": ["olores y humos", "convivencia"],
}
MOTIVO_RECHAZO = "No se puede comprobar con los documentos de la comunidad."
EN_LECTURA = {"RECEIVED", "PROCESSING", "READER_PROCESSING"}

Seleccion = namedtuple("Seleccion", "empresas proyectos documentos fichas parejas trabajadores")


def leer_json(ruta):
    return json.loads(Path(ruta).read_text(encoding="utf-8"))


# ---------- Qué se carga ----------

def seleccionar(manifiesto, claves):
    elegidos = {e: set(COMUNIDADES[e]) | ({ABIERTO[e]} if e in ABIERTO else set()) for e in COMUNIDADES}
    dentro = lambda x: x["empresa"] in elegidos and (x["proyecto"] is None or x["proyecto"] in elegidos[x["empresa"]])
    proyectos = [p for p in manifiesto["proyectos"] if p["empresa"] in elegidos and p["clave"] in elegidos[p["empresa"]]]
    documentos = [d for d in manifiesto["documentos"] if dentro(d)]
    fichas = [f for f in manifiesto["fichas"] if dentro(f) and f["proyecto"] is not None]
    rutas = {d["ruta"] for d in documentos}
    parejas = [(v, n) for v, n in sustituciones.parejas(claves, manifiesto) if v in rutas and n in rutas]
    origen = {t["clave"]: t for t in manifiesto["trabajadores"]}
    trabajadores = [{"clave": c, "empresa": origen[o]["empresa"], "nombre": origen[o]["nombre"],
                     "apellidos": origen[o]["apellidos"], "proyectos": ps} for c, o, ps in TRABAJADORES]
    empresas = [e for e in manifiesto["empresas"] if e["clave"] in elegidos]
    return Seleccion(empresas, proyectos, documentos, fichas, parejas, trabajadores)


def proyectos_de(trabajador, s):
    if trabajador["proyectos"] == ["*"]:
        return [p["clave"] for p in s.proyectos if p["empresa"] == trabajador["empresa"]]
    return list(trabajador["proyectos"])


def autor_de(ficha, s):
    """El trabajador con menos proyectos que ve el de la ficha: así Javier escribe las de sus dos comunidades."""
    candidatos = [t for t in s.trabajadores if ficha["proyecto"] in proyectos_de(t, s)]
    return min(candidatos, key=lambda t: len(proyectos_de(t, s)))


def es_caso(ruta):
    return ruta.split("/")[-1].startswith("caso-")


def temas_de(ruta):
    if not es_caso(ruta):
        return []
    return TEMAS_DE_CASO[re.sub(r"^caso-\d{4}-\d+-", "", ruta.split("/")[-1]).removesuffix(".txt")]


# ---------- Ficheros ----------

def escribir_privado(ruta, texto):
    descriptor = os.open(ruta, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as f:
        f.write(texto)
    os.chmod(ruta, 0o600)


def escribir_credenciales(carpeta, datos):
    carpeta.mkdir(parents=True, exist_ok=True)
    os.chmod(carpeta, 0o700)
    escribir_privado(carpeta / "credenciales.local.json", json.dumps(datos, ensure_ascii=False, indent=1))
    a = datos["administrador"]
    filas = [f"| `{c}` | {t['nombre']} | {t['empresa']} | {', '.join(t['proyectos'])} | `{t['clave']}` |"
             for c, t in datos["trabajadores"].items()]
    escribir_privado(carpeta / "credenciales.local.md", "\n".join([
        "# Odín · Credenciales de los datos de prueba", "",
        "Datos ficticios. Fichero local con permisos 600: no lo subas a git ni lo copies a otro sitio.", "",
        "## Panel", "", f"- Dirección: {datos['panel']}", f"- Administrador del grupo: {a['nombre']}",
        f"- Correo: `{a['email']}`", f"- Contraseña: `{a['password']}`", "",
        "## Trabajadores (una clave para el MCP cada uno)", "",
        "| Nombre para `TRABAJADOR` | Persona | Empresa | Proyectos que ve | Clave |", "|---|---|---|---|---|",
        *filas, "", "Además, cada trabajador ve lo que no tiene proyecto y los proyectos abiertos de su empresa.", "",
        "Para usar una clave en Claude Desktop: `make mcp-claude-desktop TRABAJADOR=<nombre>`.", ""]))


def multipart(nombre, contenido, tipo="application/octet-stream"):
    limite = uuid.uuid4().hex
    cabecera = (f'--{limite}\r\nContent-Disposition: form-data; name="file"; filename="{nombre}"\r\n'
                f"Content-Type: {tipo}\r\n\r\n").encode()
    return cabecera + contenido + f"\r\n--{limite}--\r\n".encode(), f"multipart/form-data; boundary={limite}"


def leer_entorno(ruta):
    entorno = {}
    for linea in Path(ruta).read_text(encoding="utf-8").splitlines():
        if "=" in linea and not linea.lstrip().startswith("#"):
            clave, valor = linea.split("=", 1)
            entorno[clave.strip()] = valor.strip().strip('"').strip("'")
    return entorno


# ---------- API ----------

class Api:
    def __init__(self, url, origen):
        self.url, self.origen, self.cookie, self.arrancada = url, origen, None, False

    def pedir(self, metodo, ruta, cuerpo=None, clave=None, esperado=(200, 201, 204), binario=None):
        cabeceras = {"Origin": self.origen}
        datos = None
        if binario:
            datos, cabeceras["Content-Type"] = binario
        elif cuerpo is not None:
            datos, cabeceras["Content-Type"] = json.dumps(cuerpo).encode(), "application/json"
        if clave:
            cabeceras["Authorization"] = f"Bearer {clave}"
        elif self.cookie:
            cabeceras["Cookie"] = self.cookie
        respuesta = self._enviar(urllib.request.Request(self.url + ruta, data=datos, method=metodo, headers=cabeceras))
        texto = respuesta.read()
        if respuesta.status not in esperado:
            raise RuntimeError(f"HTTP {respuesta.status} en {metodo} {ruta.split('?')[0]}: {texto[:300]!r}")
        return respuesta, (json.loads(texto) if texto else None)

    @staticmethod
    def _enviar(peticion):
        try:
            return urllib.request.urlopen(peticion, timeout=120)
        except urllib.error.HTTPError as error:
            return error

    def admin(self, metodo, ruta, cuerpo=None, **opciones):
        return self.pedir(metodo, ruta, cuerpo, **opciones)[1]


# ---------- Carga ----------

def primer_arranque(api, entorno):
    """Hace el primer arranque. Si la instalación ya lo tenía hecho, para sin tocar nada."""
    admin = {"nombre": "Administración de prueba", "email": "admin@grupo-prueba.invalid",
             "password": secrets.token_urlsafe(18)}
    respuesta, cuerpo = api.pedir("POST", "/v1/setup", {
        "setupToken": entorno["ODIN_SETUP_TOKEN"], "groupName": "Grupo de prueba (ficticio)",
        "adminName": admin["nombre"], "adminEmail": admin["email"], "password": admin["password"]},
        esperado=range(100, 600))
    if respuesta.status != 201:
        detalle = (cuerpo or {}).get("detail", "") if isinstance(cuerpo, dict) else ""
        raise SystemExit(f"La instalación ya tiene datos ({respuesta.status} {detalle}). No se ha cambiado nada.\n"
                         "Los datos de prueba se cargan en una instalación vacía: déjala vacía antes con\n"
                         "  make instalacion-reiniciar CONFIRMAR=BORRAR\n(hace copia de lo que hay en data/copias/).")
    api.arrancada = True
    sesion, _ = api.pedir("POST", "/v1/admin/session", {"email": admin["email"], "password": admin["password"]})
    api.cookie = sesion.headers["Set-Cookie"].split(";")[0]
    return admin


def crear_empresas(api, s):
    ids, temas = {}, {}
    for e in s.empresas:
        cid = api.admin("POST", "/v1/admin/companies", {"name": e["nombre"], "code": e["codigo"]})["id"]
        api.admin("PUT", f"/v1/admin/companies/{cid}/capabilities",
                  {"capabilities": ["SEARCH_KNOWLEDGE", "SAVE_KNOWLEDGE_CARD", "SIMILAR_CASES"]})
        temas[e["clave"]] = {t["name"]: api.admin("POST", f"/v1/admin/companies/{cid}/topics", t)["id"]
                             for t in leer_json(TEMAS)}
        ids[e["clave"]] = cid
    return ids, temas


def crear_proyectos(api, s, empresas):
    ids = {}
    for p in s.proyectos:
        cid = empresas[p["empresa"]]
        ids[p["clave"]] = api.admin("POST", f"/v1/admin/companies/{cid}/projects", {"name": p["nombre"]})["id"]
        if p.get("abierto"):
            api.admin("PATCH", f"/v1/admin/companies/{cid}/projects/{ids[p['clave']]}", {"restricted": False})
    return ids


def crear_trabajadores(api, s, empresas, proyectos):
    creados = {}
    for t in s.trabajadores:
        cid = empresas[t["empresa"]]
        miembro = api.admin("POST", f"/v1/admin/companies/{cid}/members", {
            "firstName": t["nombre"], "lastName": t["apellidos"], "email": f"{t['clave']}@{t['empresa']}.invalid",
            "roles": ["WORKER"], "projectIds": [proyectos[p] for p in proyectos_de(t, s)]})
        uid = miembro["member"]["userId"]
        clave = api.admin("POST", f"/v1/admin/companies/{cid}/members/{uid}/keys", {"label": "datos de prueba"})["key"]
        creados[t["clave"]] = {"id": uid, "clave": clave}
    return creados


def subir(api, empresa, fichero, titulo, proyecto=None, caso=False, tipo="text/plain"):
    consulta = {"title": titulo, "caseDocument": str(caso).lower()} | ({"projectId": proyecto} if proyecto else {})
    ruta = f"/v1/admin/companies/{empresa}/documents?{urllib.parse.urlencode(consulta)}"
    subida = api.admin("POST", ruta, binario=multipart(fichero.name, fichero.read_bytes(), tipo))
    return subida["document"]["id"], subida["duplicate"]


def subir_documentos(api, s, empresas, proyectos):
    ids, repetidos = {}, []
    for d in s.documentos:
        did, repetido = subir(api, empresas[d["empresa"]], ARCHIVO / "corpus" / d["ruta"], d["titulo"],
                              proyectos.get(d["proyecto"]), es_caso(d["ruta"]))
        ids[d["ruta"]] = did
        if repetido:
            repetidos.append(d["ruta"])
    escaneo, _ = subir(api, empresas["ribera"], ESCANEO, "Escaneo de prueba del lector OCR (texto ajeno a las comunidades)",
                       tipo="image/png")
    return ids, escaneo, repetidos


def listar_documentos(api, empresa, pagina=100):
    docs = []
    while True:
        parte = api.admin("GET", f"/v1/admin/companies/{empresa}/documents?offset={len(docs)}&limit={pagina}")
        docs += parte
        if len(parte) < pagina:
            return docs


def esperar_lectura(api, empresas, limite=900):
    """Espera a que no quede ningún documento en lectura. Devuelve los documentos de todas las empresas."""
    fin = time.time() + limite
    while True:
        docs = [d for cid in empresas.values() for d in listar_documentos(api, cid)]
        en_lectura = [d for d in docs if d["status"] in EN_LECTURA]
        if not en_lectura:
            return docs
        if time.time() > fin:
            raise RuntimeError(f"{len(en_lectura)} documentos siguen en lectura tras {limite} s")
        time.sleep(3)


def marcar(api, s, documentos, temas):
    for viejo, vigente in s.parejas:
        api.admin("PUT", f"/v1/admin/documents/{documentos[viejo]}/superseded-by", {"replacementId": documentos[vigente]})
    puestos = 0
    for d in s.documentos:
        for tema in temas_de(d["ruta"]):
            api.admin("PUT", f"/v1/admin/documents/{documentos[d['ruta']]}/topics/{temas[d['empresa']][tema]}")
            puestos += 1
    return puestos


def crear_fichas(api, s, proyectos, trabajadores):
    estados = {}
    for f in s.fichas:
        clave = trabajadores[autor_de(f, s)["clave"]]["clave"]
        cid = api.admin("POST", "/v1/cards", {"title": f["titulo"], "body": f["texto"],
                                              "projectId": proyectos[f["proyecto"]]}, clave=clave)["id"]
        if f["estado"] == "APPROVED":
            api.admin("POST", f"/v1/admin/cards/{cid}/review", {"decision": "APPROVE"})
        elif f["estado"] == "REJECTED":
            api.admin("POST", f"/v1/admin/cards/{cid}/review", {"decision": "REJECT", "note": MOTIVO_RECHAZO})
        estados[f["estado"]] = estados.get(f["estado"], 0) + 1
    return estados


# ---------- Comprobaciones e informe ----------

def buscar(api, clave, consulta):
    return api.admin("POST", "/v1/search", {"queries": [consulta], "limit": 20}, clave=clave)["hits"]


def comprobar_aislamiento(api, s, proyectos, trabajadores, documentos):
    """Marta encuentra la derrama de Olmo 14; Javier y Ignacio no ven nada de Olmo 14 con la misma pregunta."""
    consulta = "derrama de la fachada de Calle Olmo 14"
    olmo = {did for ruta, did in documentos.items() if ruta.startswith("ribera/c1-olmo14/")}
    if not olmo & {h["sourceId"] for h in buscar(api, trabajadores["marta"]["clave"], consulta)}:
        raise RuntimeError("Marta no encuentra los documentos de Calle Olmo 14")
    for nombre in ("javier", "ignacio"):
        vistos = olmo & {h["sourceId"] for h in buscar(api, trabajadores[nombre]["clave"], consulta)}
        if vistos:
            raise RuntimeError(f"Fuga de aislamiento: {nombre} ve {len(vistos)} documentos de Calle Olmo 14")


def estado_lectores(api, entorno, empresas, docs, escaneo):
    escaneado = next(d for d in docs if d["id"] == escaneo)
    lineas = []
    if escaneado["status"] == "PENDING_READER":
        lineas.append("Lector OCR sin configurar: el documento escaneado se queda «Esperando al lector OCR» "
                      "y se leerá solo cuando llegue el lector (README, «Lector OCR»).")
    else:
        lineas.append(f"Lector OCR: el documento escaneado está en {escaneado['status']}.")
    if not entorno.get("ODIN_TAGGER_URL"):
        lineas.append("Etiquetado sin configurar: los documentos no llevan temas de la IA. Los casos llevan los "
                      "temas puestos por el administrador de prueba (README, «Etiquetado por tema»).")
    else:
        pendientes = sum(api.admin("GET", f"/v1/admin/companies/{c}/tagging-status")["pendingDocuments"]
                         for c in empresas.values())
        lineas.append(f"Etiquetado configurado: {pendientes} documentos esperan a su ventana de etiquetado.")
    return lineas


def credenciales(entorno, api, s, admin, trabajadores):
    nombres = {p["clave"]: p["nombre"] for p in s.proyectos}
    empresas = {e["clave"]: e["nombre"] for e in s.empresas}
    panel = entorno.get("ODIN_PANEL_ORIGIN", f"http://127.0.0.1:{entorno.get('ODIN_PANEL_PORT', '8088')}")
    return {"api": api.url, "panel": panel, "administrador": admin, "trabajadores": {
        t["clave"]: {"nombre": f"{t['nombre']} {t['apellidos']}", "empresa": empresas[t["empresa"]],
                     "proyectos": [nombres[p] for p in proyectos_de(t, s) if p not in ABIERTO.values()],
                     "clave": trabajadores[t["clave"]]["clave"]} for t in s.trabajadores}}


def cargar(api, entorno, s):
    admin = primer_arranque(api, entorno)
    empresas, temas = crear_empresas(api, s)
    proyectos = crear_proyectos(api, s, empresas)
    trabajadores = crear_trabajadores(api, s, empresas, proyectos)
    documentos, escaneo, repetidos = subir_documentos(api, s, empresas, proyectos)
    docs = esperar_lectura(api, empresas)
    if len(docs) != len(set(documentos.values()) | {escaneo}):
        raise RuntimeError(f"La API lista {len(docs)} documentos y se han subido {len(set(documentos.values())) + 1}")
    fallidos = [d["title"] for d in docs if d["status"] == "FAILED"]
    if fallidos:
        raise RuntimeError(f"No se han podido leer {len(fallidos)} documentos: {fallidos[:5]}")
    temas_puestos = marcar(api, s, documentos, temas)
    fichas = crear_fichas(api, s, proyectos, trabajadores)
    comprobar_aislamiento(api, s, proyectos, trabajadores, documentos)
    escribir_credenciales(CREDENCIALES, credenciales(entorno, api, s, admin, trabajadores))
    return [f"Cargado: {len(s.empresas)} empresas, {len(s.proyectos)} proyectos ({len(COMUNIDADES['ribera']) + len(COMUNIDADES['albar'])} comunidades "
            f"y uno abierto), {len(docs)} documentos ({sum(map(es_caso, documentos))} casos, {len(s.parejas)} "
            f"sustituidos), {temas_puestos} temas en los casos, fichas {fichas}, 1 administrador y "
            f"{len(trabajadores)} trabajadores con su clave.",
            *([f"Contenido repetido, subido una sola vez: {repetidos}"] if repetidos else []),
            "Aislamiento comprobado por la API: Javier e Ignacio no ven Calle Olmo 14.",
            *estado_lectores(api, entorno, empresas, docs, escaneo),
            f"Credenciales (sin mostrarlas): {CREDENCIALES / 'credenciales.local.md'}",
            "Siguiente paso: make mcp-claude-desktop TRABAJADOR=marta"]


def volver_a_vacia():
    print("Fallo a medias: vuelvo a dejar la instalación vacía.", file=sys.stderr)
    entorno = os.environ | {"CONFIRMAR": "BORRAR", "MOTIVO": "carga-fallida"}
    subprocess.run([str(RAIZ / "deploy" / "instalacion" / "reiniciar.sh")], env=entorno, check=False)


def main():
    entorno = leer_entorno(PRINCIPAL / os.environ.get("INSTALACION_ENV", "deploy/.env"))
    api = Api(f"http://127.0.0.1:{entorno.get('ODIN_API_PORT', '8080')}", entorno.get("ODIN_PANEL_ORIGIN", ""))
    try:
        api.pedir("GET", "/v1/health")
    except (OSError, RuntimeError):
        raise SystemExit(f"La instalación no responde en {api.url}: arráncala con make up. No se ha cambiado nada.")
    s = seleccionar(leer_json(ARCHIVO / "manifiesto.json"), leer_json(ARCHIVO / "claves.json"))
    print(f"Cargando datos de prueba en {api.url}. Tarda unos minutos.")
    try:
        lineas = cargar(api, entorno, s)
    except SystemExit:
        raise
    except (Exception, KeyboardInterrupt) as error:
        print(f"Error: {error!r}", file=sys.stderr)
        if api.arrancada:
            volver_a_vacia()
        return 1
    print("\n".join(lineas))
    return 0


if __name__ == "__main__":
    sys.exit(main())
