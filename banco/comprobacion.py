"""Comprobación previa sin IA (D-094, punto 3): reglas, datos, capacidades, herramientas y ejecutor. Ninguna orden de
medición la salta: `run`, `sweep` y `reanudar` la exigen antes de gastar, de abrir el libro o de crear la carpeta.
Sin claude y sin red fuera de localhost; `http` y `mcp` se inyectan en las pruebas."""

import contextlib
import hashlib
import json
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path
from urllib.parse import urlsplit

from banco import capas
from banco.config import CAPABILITIES, ConfigError, load_config
from banco.medir_path import carga_mod, ejecutar

TOPE_USD = 5.0
EXPEDIENTES_BATTERY = "generalidad-expediente"  # sus documentos deben estar cargados en la pila
CASOS = Path(__file__).resolve().parent / "fakes" / "eventos_raros.jsonl"
NO_SE_COMPRUEBA = "no se comprueba sin carga"
LOCALES = ("127.0.0.1", "localhost", "::1")
PREAMBULO = {"type": "assistant", "message": {"content": [
    {"type": "tool_use", "id": "t1", "name": "mcp__odin__searchKnowledge", "input": {"query": "prueba"}}]}}
INICIO_MCP = {"protocolVersion": "2025-03-26", "capabilities": {},
              "clientInfo": {"name": "banco-comprobacion", "version": "1"}}
# Qué capacidades necesita cada herramienta (mcp/src/server.ts). findSimilarCases exige también SEARCH_KNOWLEDGE.
REQUIEREN = {
    "listProjects": ("SEARCH_KNOWLEDGE",), "listDocuments": ("SEARCH_KNOWLEDGE",),
    "searchKnowledge": ("SEARCH_KNOWLEDGE",), "readPage": ("SEARCH_KNOWLEDGE",),
    "verifyCitation": ("SEARCH_KNOWLEDGE",), "listMyQuestions": ("SEARCH_KNOWLEDGE",),
    "openOriginal": ("SEARCH_KNOWLEDGE",), "findSimilarCases": ("SIMILAR_CASES", "SEARCH_KNOWLEDGE"),
    "getCaseFile": ("CASE_FILES",), "readCaseFile": ("CASE_FILES",), "addToCaseFile": ("CASE_FILES",),
    "removeFromCaseFile": ("CASE_FILES",), "requestUploadLink": ("CASE_FILES",),
    "saveKnowledgeCard": ("SAVE_KNOWLEDGE_CARD",), "listMyKnowledgeCards": ("SAVE_KNOWLEDGE_CARD",),
}


class ComprobacionError(Exception):
    """La comprobación previa no pasó: ninguna medición se lanza."""


class _SinRedirecciones(urllib.request.HTTPRedirectHandler):
    """No sigue redirecciones: la clave de API nunca sale hacia otro host."""

    def redirect_request(self, *args, **kwargs):
        return None


_ABRIR = urllib.request.build_opener(_SinRedirecciones()).open


def _http(metodo, url, cabeceras, cuerpo=None):
    """Petición HTTP a localhost. Devuelve (estado, cabeceras en minúscula, texto); fuera de localhost, OSError."""
    if urlsplit(url).hostname not in LOCALES:
        raise OSError(f"la comprobación solo habla con localhost: {url}")
    data = None if cuerpo is None else json.dumps(cuerpo).encode("utf-8")
    peticion = urllib.request.Request(url, method=metodo, data=data, headers=cabeceras)
    try:
        respuesta = _ABRIR(peticion, timeout=30)
    except urllib.error.HTTPError as error:
        respuesta = error
    texto = respuesta.read().decode("utf-8")
    return respuesta.status, {k.lower(): v for k, v in respuesta.headers.items()}, texto


def _rpc(id_, metodo, params):
    return {"jsonrpc": "2.0", "id": id_, "method": metodo, "params": params}


def _respuesta(texto, id_):
    """La respuesta JSON-RPC con ese id, en JSON o en SSE («data: {...}»)."""
    lineas = [linea[5:].strip() for linea in texto.splitlines() if linea.startswith("data:")]
    for mensaje in lineas or [texto]:
        objeto = json.loads(mensaje)
        if isinstance(objeto, dict) and objeto.get("id") == id_:
            return objeto
    raise ValueError(f"sin respuesta al mensaje {id_}")


def herramientas_mcp(url, clave, http):
    """Abre una sesión MCP (initialize, notifications/initialized, tools/list) y devuelve los nombres; la cierra."""
    cab = {"Authorization": f"Bearer {clave}", "Content-Type": "application/json",
           "Accept": "application/json, text/event-stream"}
    estado, cabeceras, _ = http("POST", url, cab, _rpc(1, "initialize", INICIO_MCP))
    sid = cabeceras.get("mcp-session-id")
    if estado != 200 or not sid:
        raise ValueError(f"initialize devolvió {estado} sin sesión")
    sesion = {**cab, "mcp-session-id": sid}
    http("POST", url, sesion, {"jsonrpc": "2.0", "method": "notifications/initialized"})
    try:
        _, _, texto = http("POST", url, sesion, _rpc(2, "tools/list", {}))
        return [t["name"] for t in _respuesta(texto, 2)["result"]["tools"]]
    finally:
        with contextlib.suppress(OSError):
            http("DELETE", url, sesion, None)


def herramientas_esperadas(capacidades):
    """Las herramientas del banco cuyas capacidades están todas en la configuración."""
    return [t for t in ejecutar.HERRAMIENTAS if set(REQUIEREN[t]) <= set(capacidades)]


def reglas(cfg):
    fallos = []
    if cfg.budget_usd > TOPE_USD and not cfg.owner_approval:
        fallos.append(f"presupuesto de {cfg.budget_usd:g} USD por encima del tope de {TOPE_USD:g} USD sin «ownerApproval» "
                      "en la configuración (D-094)")
    if set(cfg.capabilities) != set(CAPABILITIES) and not cfg.owner_approval:
        fallos.append(f"se piden menos capacidades que las del producto ({', '.join(cfg.capabilities)}) sin «ownerApproval» (D-094)")
    return fallos


def _carga(layout):
    """La pila (mapas e URLs) de carga.json; si falta o no tiene la URL de la API, ConfigError en español."""
    ruta = layout.state_dir / "carga.json"
    if not ruta.is_file():
        raise ConfigError(f"no hay carga en {ruta}: ejecuta antes «make banco-cargar»")
    crudo = json.loads(ruta.read_text(encoding="utf-8"))
    stack = carga_mod.leer(ruta)
    if not crudo.get("api") or not stack["url_mcp"]:
        raise ConfigError(f"la carga de {ruta} no tiene la URL de la API o la del MCP")
    return {**stack, "api": crudo["api"]}


def _referencias(items):
    """Rutas de documento y ficha que citan las preguntas (citas, sustituidos, esperados, fichas esperadas)."""
    rutas, fichas = set(), set()
    for item in items:
        rutas |= {c["doc"] for c in item.get("citas_resueltas", [])}
        rutas |= set(item.get("sustituidos", [])) | set(item.get("esperados", []))
        fichas |= set(item.get("fichas_esperadas", []))
    return rutas, fichas


def check_loaded(folder, stack, cfg):
    """Las citas y las fichas solo se resuelven si la pila tiene cargados los documentos y fichas del manifiesto.
    Con la batería de expedientes, también los documentos de sus expedientes (con EXPEDIENTES=1)."""
    manifest = json.loads((folder / "manifiesto.json").read_text(encoding="utf-8"))
    docs, cards = set(stack["doc_ruta"].values()), set(stack["ficha_f"].values())
    arg = ",".join(cfg.archives)
    if any(d["ruta"] not in docs for d in manifest["documentos"]) or any(f["id"] not in cards for f in manifest["fichas"]):
        raise ConfigError(f"la pila no tiene cargados los documentos de {', '.join(cfg.archives)}: "
                          f"ejecuta antes «make banco-cargar ARCHIVOS={arg}»")
    if EXPEDIENTES_BATTERY in cfg.batteries and not _expediente_docs(folder) <= docs:
        raise ConfigError(f"la pila no tiene cargados los documentos de los expedientes: "
                          f"ejecuta antes «make banco-cargar ARCHIVOS={arg} EXPEDIENTES=1»")


def _expediente_docs(folder):
    """Documentos y compartidos de los expedientes: los que sube el cargador (los ajenos son señuelos y no)."""
    path = folder / "expedientes.json"
    cases = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else []
    return {ruta for case in cases for ruta in case["documentos"] + case["compartidos"]}


def datos(cfg, layout, items, stack):
    fallos = []
    sin_clave = sorted({i["actor"] for i in items} - set(stack["claves"]))
    if sin_clave:
        fallos.append(f"actores sin clave en la pila: {', '.join(sin_clave)}")
    rutas, fichas = _referencias(items)
    faltan_docs = sorted(rutas - set(stack["doc_ruta"].values()))
    if faltan_docs:
        fallos.append(f"documentos de las preguntas que no están en la pila: {', '.join(faltan_docs)}")
    faltan_fichas = sorted(fichas - set(stack["ficha_f"].values()))
    if faltan_fichas:
        fallos.append(f"fichas de las preguntas que no están en la pila: {', '.join(faltan_fichas)}")
    try:
        check_loaded(capas.data_dir(layout, cfg.archives), stack, cfg)
    except (ConfigError, capas.ArchivoError, OSError) as error:
        fallos.append(str(error))
    return fallos


def _capacidades_de(actor, pedidas, clave, api, http):
    estado, _, texto = http("GET", f"{api}/v1/me", {"Authorization": f"Bearer {clave}"}, None)
    if estado != 200:
        return [f"{actor}: GET /v1/me devolvió {estado}, se esperaba 200"]
    ofrecidas = json.loads(texto)["company"]["capabilities"]
    faltan = [c for c in pedidas if c not in ofrecidas]
    return [f"{actor}: a la empresa le faltan las capacidades {', '.join(faltan)}"] if faltan else []


def _herramientas_de(actor, esperadas, clave, url, mcp):
    faltan = [t for t in esperadas if t not in mcp(url, clave)]
    return [f"{actor}: el MCP no ofrece las herramientas {', '.join(faltan)}"] if faltan else []


def _seguro(seccion, *args):
    """Un fallo de red, de formato o de datos se convierte en un hallazgo: la comprobación nunca pasa por error."""
    try:
        return seccion(*args)
    except (OSError, ValueError, KeyError, TypeError) as error:
        return [f"no se pudo completar la comprobación: {error}"]


def capacidades(cfg, actores, stack, http):
    return [m for a in actores for m in _seguro(_capacidades_de, a, cfg.capabilities, stack["claves"][a], stack["api"], http)]


def herramientas(cfg, actores, stack, mcp):
    esperadas = herramientas_esperadas(cfg.capabilities)
    if not esperadas:
        return [f"no se espera ninguna herramienta con las capacidades {', '.join(cfg.capabilities)}: revisa «capabilities»"]
    return [m for a in actores for m in _seguro(_herramientas_de, a, esperadas, stack["claves"][a], stack["url_mcp"], mcp)]


def fallos_ejecutor(lineas=None):
    """Eventos raros de stream-json por el lector del ejecutor, caso a caso; un fallo se reporta con su nombre."""
    lineas = CASOS.read_text(encoding="utf-8").splitlines() if lineas is None else lineas
    fallos = []
    for n, linea in enumerate(lineas, start=1):
        caso = f"la línea {n}"
        try:
            ev = ejecutar._json(linea)
            if ev:
                caso = ev.get("_caso", caso)
                ejecutar.leer_consultas([PREAMBULO, ev])
        except Exception as error:  # el ejecutor puede fallar de cualquier forma: eso es lo que se reporta
            fallos.append(f"el ejecutor falla con {caso}: {error}")
    return fallos


def comprobar(cfg, layout, http=None, mcp=None):
    """Secciones con sus fallos (lista vacía = pasa). Sin carga, las secciones que dependen de ella fallan cerradas."""
    http = http or _http
    mcp = mcp or (lambda url, clave: herramientas_mcp(url, clave, http))
    secciones = {"reglas": reglas(cfg), "ejecutor": fallos_ejecutor()}
    try:
        stack = _carga(layout)
        items = capas.load_items(layout, cfg.batteries)
    except (ConfigError, capas.ArchivoError, ValueError) as error:
        return {**secciones, "datos": [str(error)], "capacidades": [NO_SE_COMPRUEBA], "herramientas": [NO_SE_COMPRUEBA]}
    actores = sorted({i["actor"] for i in items if i["actor"] in stack["claves"]})
    secciones["datos"] = datos(cfg, layout, items, stack)
    secciones["capacidades"] = capacidades(cfg, actores, stack, http)
    secciones["herramientas"] = herramientas(cfg, actores, stack, mcp)
    return secciones


def check(cfg, layout, http=None, mcp=None):
    """Los fallos en español, aplanados; lista vacía = pasa."""
    return [m for mensajes in comprobar(cfg, layout, http, mcp).values() for m in mensajes]


def previa(config_path, layout, http=None, mcp=None):
    """Comprueba la configuración y guarda el informe en el estado (comprobacion-<nombre>.json). No lanza por fallos."""
    cfg = load_config(Path(config_path), layout.banco)
    secciones = comprobar(cfg, layout, http, mcp)
    informe = {"fecha": datetime.now().isoformat(timespec="seconds"), "configuracion": cfg.name,
               "config_sha256": hashlib.sha256(Path(config_path).read_bytes()).hexdigest(),
               "comprobaciones": {n: {"resultado": "fallo" if m else "ok", "mensajes": m} for n, m in secciones.items()},
               "fallos": [m for mensajes in secciones.values() for m in mensajes]}
    layout.state_dir.mkdir(parents=True, exist_ok=True)
    (layout.state_dir / f"comprobacion-{cfg.name}.json").write_text(_texto(informe), encoding="utf-8")
    return informe


def _texto(informe):
    return json.dumps(informe, ensure_ascii=False, indent=1)


def exigir(informe):
    """Lanza ComprobacionError si el informe tiene fallos; si no, lo devuelve. Ninguna orden sin esto."""
    if informe["fallos"]:
        raise ComprobacionError(f"comprobación previa fallida (D-094): {'; '.join(informe['fallos'])}")
    return informe


def guardar_en(informe, carpeta):
    (carpeta / "COMPROBACION.json").write_text(_texto(informe), encoding="utf-8")


def resumen(informe):
    estado = "fallida" if informe["fallos"] else "superada"
    lineas = [f"comprobación previa «{informe['configuracion']}» (D-094): {estado}"]
    for nombre, seccion in informe["comprobaciones"].items():
        lineas.append(f"  {nombre}: {seccion['resultado']}")
        lineas += [f"    - {mensaje}" for mensaje in seccion["mensajes"]]
    return "\n".join(lineas)
