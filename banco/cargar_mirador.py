"""Carga la batería de Mirador en la pila del banco por la API de administración: proyecto nuevo en Ribera con solo t01
como miembro, documentos (y los de sus expedientes), sustituciones, fichas y revisiones. Precondición: la pila está
arriba y el archivo ficticio cargado con todas las capacidades (carga.json con t01 y t02). Sin IA ni red fuera de
localhost. Uso: python3 banco/cargar_mirador.py   (con ODIN_BANCO_MIRADOR_DATOS=<carpeta datos/ en formato CTO>)"""

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from collections import namedtuple
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from banco import propietario  # noqa: E402
from banco.layout import default_layout  # noqa: E402

COMPANY_CODE = "RIB"
ACTOR = "t01"
REQUIRED_ACTORS = ("t01", "t02")
CARD_STATES = {"PENDING", "APPROVED", "REJECTED", "CORRECTED"}
READING = {"RECEIVED", "PROCESSING", "PENDING_READER", "READER_PROCESSING"}
MIME_TYPES = {".pdf": "application/pdf", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png",
              ".csv": "text/csv", ".txt": "text/plain"}
REJECT_NOTE = "Rechazada en la revisión."
LIMIT_S = 3600
INTERVAL_S = 3
LOCAL_HOSTS = ("127.0.0.1", "localhost", "::1")
Result = namedtuple("Result", "lines failed")


class LoadError(Exception):
    """La carga de Mirador no se puede hacer: falta algo, ya está cargada o los datos no cuadran. Nada se cambia antes."""


class NotFoundError(LoadError):
    """La API responde 404: la pila no tiene esa operación (p. ej. una versión sin expedientes)."""


PARTIAL_MESSAGE = ("Carga a medias: la pila del banco es desechable; rehazla con make banco-abajo, make banco-arriba, "
                   "make banco-cargar … y make banco-cargar-mirador.")
CASE_FILE_MISSING = "la pila no tiene la API de expedientes: los documentos quedan en el proyecto sin asignar"


class _NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


_OPEN = urllib.request.build_opener(urllib.request.ProxyHandler({}), _NoRedirects()).open


def _http(method, url, headers, body):
    """Petición a localhost. Devuelve (estado, cabeceras en minúscula, texto); fuera de localhost, LoadError."""
    if urllib.parse.urlsplit(url).hostname not in LOCAL_HOSTS:
        raise LoadError(f"la carga solo habla con localhost: {url}")
    request = urllib.request.Request(url, method=method, data=body, headers=headers)
    try:
        response = _OPEN(request, timeout=120)
    except urllib.error.HTTPError as error:
        response = error
    return response.status, {k.lower(): v for k, v in response.headers.items()}, response.read().decode("utf-8")


def email_of(key):
    """El correo que cargar.py puso a cada trabajador de prueba."""
    return f"{key}@medicion.invalid"


def read_env(state_dir):
    path = state_dir / "medicion.env"
    if not path.is_file():
        raise LoadError(f"falta {path}: la pila no está arriba («make banco-arriba»)")
    return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines() if "=" in line)


def read_stack(state_dir):
    """carga.json con la API y las claves de t01 y t02: lo deja la carga del archivo ficticio con todas las capacidades."""
    path = state_dir / "carga.json"
    if not path.is_file():
        raise LoadError(f"no hay carga en {path}: ejecuta antes «make banco-cargar CARGAR_OPCIONES=--todas-las-capacidades»")
    raw = json.loads(path.read_text(encoding="utf-8"))
    if any(actor not in raw.get("claves", {}) for actor in REQUIRED_ACTORS) or not raw.get("api"):
        raise LoadError(f"la carga de {path} no tiene las claves de {' y '.join(REQUIRED_ACTORS)} o la URL de la API")
    return raw


def mime_of(name):
    mime = MIME_TYPES.get(Path(name).suffix.lower())
    if mime is None:
        raise LoadError(f"tipo no admitido para {name}: solo {', '.join(sorted(MIME_TYPES))}")
    return mime


def _problems_documents(manifest, folder):
    problems = []
    for doc in manifest["documentos"]:
        if not (folder / "corpus" / doc["ruta"]).is_file():
            problems.append(f"falta el fichero corpus/{doc['ruta']}")
        if Path(doc["ruta"]).suffix.lower() not in MIME_TYPES:
            problems.append(f"tipo no admitido para {doc['ruta']}")
    return problems


def _problems_cases(cases, manifest, folder):
    doc_paths = {doc["ruta"] for doc in manifest["documentos"]}
    problems = []
    for case in cases:
        if case["caso"] not in doc_paths:
            problems.append(f"expediente sin su caso en el manifiesto: {case['caso']}")
        for p in case["documentos"] + case["compartidos"]:
            if Path(p).suffix.lower() not in MIME_TYPES:
                problems.append(f"tipo no admitido para {p}")
            if not (folder / "expedientes" / p).is_file():
                problems.append(f"falta el fichero expedientes/{p}")
    return problems


def _problems_cards(manifest):
    problems = [f"ficha {c['id']}: estado {c['estado']} desconocido" for c in manifest["fichas"] if c["estado"] not in CARD_STATES]
    return problems + [f"ficha {c['id']}: CORRECTED sin correccion" for c in manifest["fichas"]
                       if c["estado"] == "CORRECTED" and not c.get("correccion")]


def read_data(folder):
    """Manifiesto y expedientes de la carpeta CTO; todo se revisa antes de tocar la pila."""
    folder = Path(folder)
    if not (folder / "manifiesto.json").is_file():
        raise LoadError(f"falta {folder / 'manifiesto.json'} (ODIN_BANCO_MIRADOR_DATOS)")
    manifest = json.loads((folder / "manifiesto.json").read_text(encoding="utf-8"))
    cases_path = folder / "expedientes.json"
    cases = json.loads(cases_path.read_text(encoding="utf-8")) if cases_path.is_file() else []
    failures = ["el manifiesto tiene que tener exactamente un proyecto"] if len(manifest["proyectos"]) != 1 else []
    failures += _problems_documents(manifest, folder) + _problems_cases(cases, manifest, folder) + _problems_cards(manifest)
    if failures:
        raise LoadError("los datos de Mirador no se pueden cargar. No se ha cambiado nada:\n- " + "\n- ".join(failures))
    return manifest, cases


def multipart(file_name, content, mime):
    """Cuerpo multipart/form-data con el fichero en el campo «file» (lo que pide la API de subida)."""
    boundary = uuid.uuid4().hex
    head = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{file_name}\"\r\n"
            f"Content-Type: {mime}\r\n\r\n").encode("utf-8")
    return head + content + f"\r\n--{boundary}--\r\n".encode("utf-8"), f"multipart/form-data; boundary={boundary}"


class Api:
    """Cliente mínimo de la API de administración: sesión de administrador, o la clave de API de un trabajador."""

    def __init__(self, base, http):
        self.base = base
        self.http = http
        self.cookie = None

    def login(self, email, password):
        status, headers, _ = self.http("POST", f"{self.base}/v1/admin/session", {"Content-Type": "application/json"},
                                       json.dumps({"email": email, "password": password}).encode("utf-8"))
        if status != 200 or "set-cookie" not in headers:
            raise LoadError(f"no se pudo entrar como administrador (HTTP {status})")
        self.cookie = headers["set-cookie"].split(";")[0]

    def call(self, method, path, payload=None, key=None, raw=None):
        headers = {"Authorization": f"Bearer {key}"} if key else {"Cookie": self.cookie}
        if raw is not None:
            data, headers["Content-Type"] = raw
        elif payload is not None:
            data, headers["Content-Type"] = json.dumps(payload).encode("utf-8"), "application/json"
        else:
            data = None
        status, _, text = self.http(method, self.base + path, headers, data)
        if not 200 <= status < 300:
            error_class = NotFoundError if status == 404 else LoadError
            raise error_class(f"HTTP {status} en {method} {path}: {text[:300]}")
        return json.loads(text) if text else None


def find_company(api):
    for company in api.call("GET", "/v1/admin/companies"):
        if company["code"] == COMPANY_CODE:
            return company["id"]
    raise LoadError(f"no hay empresa con código {COMPANY_CODE} en la pila")


def create_project(api, company_id, name):
    names = {p["name"] for p in api.call("GET", f"/v1/admin/companies/{company_id}/projects")}
    if name in names:
        raise LoadError(f"El proyecto «{name}» ya está en la pila. No se ha cambiado nada.")
    return api.call("POST", f"/v1/admin/companies/{company_id}/projects", {"name": name})["id"]


def add_actor_member(api, company_id, project_id):
    users = {m["email"]: m["userId"] for m in api.call("GET", f"/v1/admin/companies/{company_id}/members")}
    if email_of(ACTOR) not in users:
        raise LoadError(f"{ACTOR} no está en la empresa: ejecuta antes «make banco-cargar CARGAR_OPCIONES=--todas-las-capacidades»")
    api.call("PUT", f"/v1/admin/companies/{company_id}/projects/{project_id}/members/{users[email_of(ACTOR)]}")


def upload(api, company_id, project_id, file, title, case_doc):
    query = urllib.parse.urlencode({"title": title, "projectId": project_id, "caseDocument": str(case_doc).lower()})
    body = multipart(file.name, file.read_bytes(), mime_of(file.name))
    return api.call("POST", f"/v1/admin/companies/{company_id}/documents?{query}", raw=body)["document"]["id"]


def upload_documents(api, company_id, project_id, manifest, cases, folder):
    """Ruta → documentId: los documentos del manifiesto (caso-… como caso) y los de los expedientes, sin repetir."""
    ids = {}
    for doc in manifest["documentos"]:
        case_doc = Path(doc["ruta"]).name.startswith("caso-")
        ids[doc["ruta"]] = upload(api, company_id, project_id, folder / "corpus" / doc["ruta"], doc["titulo"], case_doc)
    for path in sorted({p for case in cases for p in case["documentos"] + case["compartidos"]} - set(ids)):
        ids[path] = upload(api, company_id, project_id, folder / "expedientes" / path, Path(path).name, False)
    return ids


def all_documents(api, company_id, page_size=200):
    documents, offset = [], 0
    while True:
        page = api.call("GET", f"/v1/admin/companies/{company_id}/documents?offset={offset}&limit={page_size}")
        documents += page
        offset += len(page)
        if len(page) < page_size:
            return documents


def wait_reader(api, company_id, ids, clock=time.time, sleep=time.sleep, limit=LIMIT_S):
    """Espera a que ningún documento subido siga en lectura. Devuelve id → estado final (READY o FAILED)."""
    deadline = clock() + limit
    while True:
        statuses = {d["id"]: d["status"] for d in all_documents(api, company_id) if d["id"] in ids}
        pending = [i for i in ids if statuses.get(i, "RECEIVED") in READING]
        if not pending:
            return statuses
        if clock() > deadline:
            raise LoadError(f"Documentos todavía en lectura tras {limit} s: {len(pending)} sin terminar")
        sleep(INTERVAL_S)


def mark_case(api, company_id, case_id):
    if not api.call("GET", f"/v1/admin/documents/{case_id}").get("caseDocument"):
        api.call("POST", f"/v1/admin/companies/{company_id}/documents/case-marks", {"documentIds": [case_id], "caseDocument": True})


def attach_cases(api, company_id, ids, cases, statuses):
    """Cada expediente: su caso marcado como caso y sus documentos y compartidos asignados a él. Devuelve avisos.
    Sin la API de expedientes (404) avisa una sola vez y no asigna nada más."""
    notes = []
    for case in cases:
        case_id = ids[case["caso"]]
        if statuses.get(case_id) != "READY":
            notes.append(f"el caso {case['caso']} no está READY: sus documentos no se asignan")
            continue
        mark_case(api, company_id, case_id)
        doc_ids = [ids[p] for p in case["documentos"] + case["compartidos"]]
        try:
            api.call("POST", f"/v1/admin/companies/{company_id}/cases/{case_id}/file/documents", {"documentIds": doc_ids})
        except NotFoundError:
            return notes + [CASE_FILE_MISSING]
    return notes


def supersede(api, ids, supersessions):
    for s in supersessions:
        api.call("PUT", f"/v1/admin/documents/{ids[s['viejo']]}/superseded-by", {"replacementId": ids[s["nuevo"]]})


def review_of(card):
    if card["estado"] == "APPROVED":
        return {"decision": "APPROVE"}
    if card["estado"] == "CORRECTED":
        return {"decision": "CORRECT", "correctedBody": card["correccion"]}
    if card["estado"] == "REJECTED":
        return {"decision": "REJECT", "note": card.get("nota", REJECT_NOTE)}
    return None


def create_cards(api, cards, project_id, key):
    """cardId → id de la ficha del manifiesto. Las fichas se crean con la clave de t01 y se revisan como dice su estado."""
    ids = {}
    for card in cards:
        card_id = api.call("POST", "/v1/cards", {"title": card["titulo"], "body": card["texto"], "projectId": project_id},
                           key=key)["id"]
        ids[card_id] = card["id"]
        review = review_of(card)
        if review:
            api.call("POST", f"/v1/admin/cards/{card_id}/review", review)
    return ids


def write_atomic(path, data):
    """Temporal y cambio de nombre: un corte no deja carga.json a medias. Solo lo lee su dueño (600), también si existía."""
    tmp = path.with_name(path.name + ".tmp")
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    os.fchmod(fd, 0o600)  # un temporal viejo con otros permisos no se hereda
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(json.dumps(data, ensure_ascii=False, indent=1))
    os.replace(tmp, path)


def annotate_stack(state_dir, documents=None, cards=None):
    """Añade a carga.json los mapas nuevos (documentId → ruta, cardId → id de ficha), sin quitar lo anterior."""
    path = state_dir / "carga.json"
    raw = json.loads(path.read_text(encoding="utf-8"))
    raw["documentos"] = {**raw.get("documentos", {}), **(documents or {})}
    raw["fichas"] = {**raw.get("fichas", {}), **(cards or {})}
    write_atomic(path, raw)


def report(project_name, ids, cards, statuses, notes):
    failed = sorted(path for path, doc_id in ids.items() if statuses.get(doc_id) == "FAILED")
    counts = {s: sum(1 for v in statuses.values() if v == s) for s in sorted(set(statuses.values()))}
    lines = [f"Proyecto «{project_name}» creado; lo ve {ACTOR} y los administradores.",
             f"Documentos subidos: {len(ids)} ({', '.join(f'{s} {n}' for s, n in counts.items())}).",
             f"Fichas creadas: {len(cards)}.", *notes]
    return Result(lines, failed)


def _populate(api, state_dir, company_id, project_id, project_name, job):
    """Todo lo que va después de crear el proyecto. Los ids se anotan en carga.json según se crean."""
    manifest, cases, folder, stack, timing = job
    add_actor_member(api, company_id, project_id)
    ids = upload_documents(api, company_id, project_id, manifest, cases, folder)
    annotate_stack(state_dir, documents={doc_id: path for path, doc_id in ids.items()})
    statuses = wait_reader(api, company_id, set(ids.values()), *timing)
    notes = attach_cases(api, company_id, ids, cases, statuses)
    supersede(api, ids, manifest.get("sustituciones", []))
    cards = create_cards(api, manifest["fichas"], project_id, stack["claves"][ACTOR])
    annotate_stack(state_dir, cards=cards)
    return report(project_name, ids, cards, statuses, notes)


def load(state_dir, folder, http, clock=time.time, sleep=time.sleep, limit=LIMIT_S):
    """Carga Mirador de punta a punta. Antes de cualquier llamada revisa carga (con la guarda D-099), datos y env."""
    stack = read_stack(state_dir)
    propietario.proteger_banco(urls=[u for u in (stack["api"], stack.get("mcp")) if u])
    manifest, cases = read_data(folder)
    env = read_env(state_dir)
    api = Api(stack["api"], http)
    api.login(env["ODIN_ADMIN_EMAIL"], env["ODIN_ADMIN_PASSWORD"])
    company_id = find_company(api)
    project_name = manifest["proyectos"][0]["nombre"]
    project_id = create_project(api, company_id, project_name)
    job = (manifest, cases, Path(folder), stack, (clock, sleep, limit))
    try:
        return _populate(api, state_dir, company_id, project_id, project_name, job)
    except Exception as error:  # cualquier fallo tras crear el proyecto deja la pila a medias
        raise LoadError(f"{error}. {PARTIAL_MESSAGE}") from error


def main(argv=None):
    try:
        propietario.proteger_banco()
    except propietario.PropietarioError as error:
        print(error, file=sys.stderr)
        return 1
    folder = os.environ.get("ODIN_BANCO_MIRADOR_DATOS")
    if not folder:
        print("error: falta ODIN_BANCO_MIRADOR_DATOS (carpeta datos/ de Mirador en formato CTO)", file=sys.stderr)
        return 1
    try:
        result = load(default_layout().state_dir, Path(folder), _http)
    except (LoadError, OSError, ValueError, KeyError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    print("\n".join(result.lines))
    if result.failed:
        print(f"No se han podido leer {len(result.failed)} documentos: {', '.join(result.failed)}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
