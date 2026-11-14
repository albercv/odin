"""Pruebas de la carga de Mirador con una API de administración falsa: sin red, sin Docker y sin IA."""
import json
import os
import re
import stat
import subprocess
import tempfile
import unittest
import urllib.parse
from pathlib import Path
from unittest import mock

from banco import cargar_mirador, propietario
from banco.cargar_mirador import LoadError
from banco.config import load_config

BANCO = Path(__file__).resolve().parent
REPO = BANCO.parent
PROYECTO = "Mirador de prueba"


class FakeOdin:
    """API falsa: responde lo que pide la carga y anota cada llamada (método, ruta, cabeceras, cuerpo)."""

    def __init__(self, proyectos=(), sin_t01=False, estados=None):
        self.llamadas = []
        self.proyectos = list(proyectos)
        self.sin_t01 = sin_t01
        self.estados = {k: list(v) for k, v in (estados or {}).items()}  # título -> estados sucesivos
        self.documentos = {}  # id -> título
        self.n = 0  # ids de documento
        self.tarjetas = 0  # ids de ficha, en su propia cuenta

    def __call__(self, metodo, url, cabeceras, cuerpo):
        partes = urllib.parse.urlsplit(url)
        self.llamadas.append((metodo, partes.path, cabeceras, cuerpo, urllib.parse.parse_qs(partes.query)))
        return self._responde(metodo, partes.path, urllib.parse.parse_qs(partes.query), cabeceras, cuerpo)

    def _responde(self, metodo, ruta, query, cabeceras, cuerpo):
        if (metodo, ruta) == ("POST", "/v1/admin/session"):
            return 200, {"set-cookie": "odin_session=s1; Path=/"}, "{}"
        if (metodo, ruta) == ("GET", "/v1/admin/companies"):
            return 200, {}, json.dumps([{"id": "c1", "name": "Ribera", "code": "RIB"}])
        if ruta == "/v1/admin/companies/c1/projects":
            return self._proyectos(metodo, cuerpo)
        if (metodo, ruta) == ("GET", "/v1/admin/companies/c1/members"):
            return 200, {}, json.dumps(self._miembros())
        if re.fullmatch(r"/v1/admin/companies/c1/projects/p-\d+/members/u-t0\d", ruta):
            return 204, {}, ""
        if (metodo, ruta) == ("POST", "/v1/admin/companies/c1/documents"):
            return self._sube(query, cuerpo)
        if (metodo, ruta) == ("GET", "/v1/admin/companies/c1/documents"):
            return 200, {}, json.dumps(self._lista())
        if re.fullmatch(r"/v1/admin/documents/d-\d+/superseded-by", ruta):
            return 200, {}, "{}"
        if (metodo, ruta) == ("POST", "/v1/cards"):
            self.tarjetas += 1
            return 201, {}, json.dumps({"id": f"k-{self.tarjetas}", "status": "SAVED", "message": "ok"})
        if re.fullmatch(r"/v1/admin/cards/k-\d+/review", ruta):
            return 200, {}, "{}"
        raise AssertionError(f"ruta no prevista: {metodo} {ruta}")

    def _proyectos(self, metodo, cuerpo):
        if metodo == "GET":
            return 200, {}, json.dumps([{"id": f"p-{i}", "name": n} for i, n in enumerate(self.proyectos, 1)])
        self.proyectos.append(json.loads(cuerpo)["name"])
        return 201, {}, json.dumps({"id": f"p-{len(self.proyectos)}"})

    def _miembros(self):
        miembros = [{"email": "t02@medicion.invalid", "userId": "u-t02"}]
        return miembros if self.sin_t01 else [{"email": "t01@medicion.invalid", "userId": "u-t01"}, *miembros]

    def _sube(self, query, cuerpo):
        self.n += 1
        did = f"d-{self.n}"
        self.documentos[did] = query["title"][0]
        return 201, {}, json.dumps({"document": {"id": did, "status": "PROCESSING"}, "duplicate": False})

    def _lista(self):
        return [{"id": did, "title": titulo, "status": self._estado(titulo)} for did, titulo in self.documentos.items()]

    def _estado(self, titulo):
        cola = self.estados.get(titulo)
        if not cola:
            return "READY"
        return cola.pop(0) if len(cola) > 1 else cola[0]

    def llamadas_a(self, metodo, ruta_regex):
        return [c for c in self.llamadas if c[0] == metodo and re.fullmatch(ruta_regex, c[1])]

    def id_de(self, titulo):
        return next(did for did, t in self.documentos.items() if t == titulo)


class Clock:
    """Reloj y «sleep» de mentira: dormir adelanta el tiempo."""

    def __init__(self):
        self.now = 0.0
        self.waits = 0

    def __call__(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds
        self.waits += 1


def prepare(root):
    """Estado (carga.json con t01 y t02, medicion.env) y carpeta de datos de Mirador en formato CTO."""
    estado, datos = root / "estado", root / "datos"
    (estado).mkdir()
    (estado / "carga.json").write_text(json.dumps({
        "api": "http://127.0.0.1:48080", "mcp": "http://127.0.0.1:48090/",
        "claves": {"t01": "clave-t01", "t02": "clave-t02"}, "documentos": {}, "fichas": {}}), encoding="utf-8")
    (estado / "medicion.env").write_text("ODIN_ADMIN_EMAIL=admin@medicion.invalid\nODIN_ADMIN_PASSWORD=prueba-local\n",
                                         encoding="utf-8")
    write_data(datos)
    return estado, datos


def write_data(datos):
    documentos = [
        {"ruta": "ribera/_general/caso-viejo.txt", "titulo": "Caso viejo", "empresa": "ribera", "proyecto": "mirador"},
        {"ruta": "ribera/_general/norma.pdf", "titulo": "Norma", "empresa": "ribera", "proyecto": "mirador"},
        {"ruta": "ribera/_general/norma-vieja.txt", "titulo": "Norma vieja", "empresa": "ribera", "proyecto": "mirador"},
    ]
    fichas = [
        {"id": "FM01", "titulo": "A", "texto": "Texto A", "estado": "APPROVED"},
        {"id": "FM02", "titulo": "B", "texto": "Texto B", "estado": "CORRECTED", "correccion": "Texto B corregido"},
        {"id": "FM03", "titulo": "C", "texto": "Texto C", "estado": "REJECTED", "nota": "No procede"},
        {"id": "FM04", "titulo": "D", "texto": "Texto D", "estado": "PENDING"},
    ]
    manifiesto = {"semilla": 7, "empresas": [{"clave": "ribera", "nombre": "Ribera", "codigo": "RIB"}],
                  "proyectos": [{"clave": "mirador", "nombre": PROYECTO, "empresa": "ribera"}],
                  "trabajadores": [], "documentos": documentos, "fichas": fichas,
                  "sustituciones": [{"viejo": "ribera/_general/norma-vieja.txt", "nuevo": "ribera/_general/norma.pdf"}]}
    (datos / "corpus" / "ribera" / "_general").mkdir(parents=True)
    (datos / "manifiesto.json").write_text(json.dumps(manifiesto, ensure_ascii=False), encoding="utf-8")
    (datos / "corpus/ribera/_general/caso-viejo.txt").write_text("=== Página 1 ===\nCaso\n", encoding="utf-8")
    (datos / "corpus/ribera/_general/norma.pdf").write_bytes(b"%PDF-prueba")
    (datos / "corpus/ribera/_general/norma-vieja.txt").write_text("Norma antigua\n", encoding="utf-8")
    (datos / "expedientes" / "ribera" / "exp").mkdir(parents=True)
    (datos / "expedientes" / "ribera" / "exp" / "c1.txt").write_text("Expediente\n", encoding="utf-8")
    (datos / "expedientes.json").write_text(json.dumps([{
        "caso": "ribera/_general/caso-viejo.txt", "documentos": ["ribera/exp/c1.txt"], "compartidos": [], "ajenos": []}]),
        encoding="utf-8")


class CargarMiradorTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.estado, self.datos = prepare(self.root)
        self.clock = Clock()

    def load(self, api):
        return cargar_mirador.load(self.estado, self.datos, api, clock=self.clock, sleep=self.clock.sleep)

    def stack_file(self):
        return json.loads((self.estado / "carga.json").read_text(encoding="utf-8"))

    def test_creates_project_with_only_t01_as_member(self):
        api = FakeOdin()
        self.load(api)
        self.assertEqual(len(api.llamadas_a("POST", r"/v1/admin/companies/c1/projects")), 1)
        self.assertEqual(api.llamadas_a("POST", r"/v1/admin/companies/c1/projects")[0][3].decode(),
                         json.dumps({"name": PROYECTO}))
        miembros = api.llamadas_a("PUT", r"/v1/admin/companies/c1/projects/p-\d+/members/.*")
        self.assertEqual([m[1].rsplit("/", 1)[1] for m in miembros], ["u-t01"])

    def test_uploads_every_document_with_case_flag_and_mime(self):
        api = FakeOdin()
        self.load(api)
        subidas = api.llamadas_a("POST", r"/v1/admin/companies/c1/documents")
        flags = {c[4]["title"][0]: c[4]["caseDocument"][0] for c in subidas}
        self.assertEqual(flags["Caso viejo"], "true")
        self.assertEqual(flags["Norma"], "false")
        pdf = next(c for c in subidas if c[4]["title"][0] == "Norma")
        self.assertIn(b"Content-Type: application/pdf", pdf[3])
        self.assertIn(b'name="file"', pdf[3])
        self.assertIn(b"%PDF-prueba", pdf[3])
        self.assertIn(b"Content-Type: text/plain", subidas[0][3])

    def test_expediente_documents_are_uploaded_too(self):
        api = FakeOdin()
        self.load(api)
        titulos = [c[4]["title"][0] for c in api.llamadas_a("POST", r"/v1/admin/companies/c1/documents")]
        self.assertIn("c1.txt", titulos)

    def test_supersedes_old_document_with_its_replacement(self):
        api = FakeOdin()
        self.load(api)
        marcas = api.llamadas_a("PUT", r"/v1/admin/documents/d-\d+/superseded-by")
        self.assertEqual(len(marcas), 1)
        self.assertEqual(marcas[0][1], f"/v1/admin/documents/{api.id_de('Norma vieja')}/superseded-by")
        self.assertEqual(json.loads(marcas[0][3]), {"replacementId": api.id_de("Norma")})

    def test_cards_are_created_with_t01_key_and_reviewed_by_state(self):
        api = FakeOdin()
        self.load(api)
        creadas = api.llamadas_a("POST", r"/v1/cards")
        self.assertEqual(len(creadas), 4)
        self.assertTrue(all(c[2]["Authorization"] == "Bearer clave-t01" for c in creadas))
        revisiones = [json.loads(c[3]) for c in api.llamadas_a("POST", r"/v1/admin/cards/k-\d+/review")]
        self.assertEqual(revisiones, [
            {"decision": "APPROVE"},
            {"decision": "CORRECT", "correctedBody": "Texto B corregido"},
            {"decision": "REJECT", "note": "No procede"},
        ])

    def test_carga_json_gets_document_and_card_maps_with_private_mode(self):
        api = FakeOdin()
        self.load(api)
        crudo = self.stack_file()
        self.assertEqual(crudo["documentos"][api.id_de("Norma")], "ribera/_general/norma.pdf")
        self.assertEqual(crudo["fichas"]["k-1"], "FM01")
        self.assertEqual(crudo["fichas"]["k-4"], "FM04")
        self.assertEqual(crudo["claves"]["t01"], "clave-t01")
        self.assertEqual(stat.S_IMODE(os.stat(self.estado / "carga.json").st_mode), 0o600)
        self.assertFalse((self.estado / "carga.json.tmp").exists())

    def test_waits_until_documents_leave_the_reader(self):
        api = FakeOdin(estados={"Caso viejo": ["PROCESSING", "PROCESSING", "READY"]})
        self.load(api)
        self.assertGreaterEqual(self.clock.waits, 2)

    def test_failed_document_is_reported_and_kept_in_carga(self):
        api = FakeOdin(estados={"Norma": ["FAILED"]})
        result = self.load(api)
        self.assertEqual(result.failed, ["ribera/_general/norma.pdf"])
        self.assertIn(api.id_de("Norma"), self.stack_file()["documentos"])

    def test_still_reading_after_the_limit_is_an_error(self):
        api = FakeOdin(estados={"Norma": ["PROCESSING"]})
        with self.assertRaisesRegex(LoadError, "tras 3600 s"):
            cargar_mirador.load(self.estado, self.datos, api, clock=self.clock, sleep=self.clock.sleep, limit=3600)

    def test_existing_project_is_refused_before_uploading(self):
        api = FakeOdin(proyectos=[PROYECTO])
        with self.assertRaisesRegex(LoadError, "ya está en la pila"):
            self.load(api)
        self.assertEqual(api.llamadas_a("POST", r"/v1/admin/companies/c1/documents"), [])

    def test_second_run_is_refused_by_the_project_check(self):
        api = FakeOdin()
        self.load(api)
        with self.assertRaisesRegex(LoadError, "ya está en la pila"):
            self.load(api)

    def test_missing_t01_in_company_is_refused(self):
        api = FakeOdin(sin_t01=True)
        with self.assertRaisesRegex(LoadError, "t01 no está en la empresa"):
            self.load(api)

    def test_unsupported_extension_is_refused_before_any_call(self):
        write_data_with(self.datos, "ribera/_general/norma.pdf", "ribera/_general/norma.docx")
        api = FakeOdin()
        with self.assertRaisesRegex(LoadError, "tipo no admitido"):
            self.load(api)
        self.assertEqual(api.llamadas, [])

    def test_missing_carga_json_asks_for_the_precondition(self):
        (self.estado / "carga.json").unlink()
        with self.assertRaisesRegex(LoadError, "make banco-cargar CARGAR_OPCIONES=--todas-las-capacidades"):
            self.load(FakeOdin())

    def test_carga_without_t02_is_refused(self):
        crudo = self.stack_file()
        del crudo["claves"]["t02"]
        (self.estado / "carga.json").write_text(json.dumps(crudo), encoding="utf-8")
        with self.assertRaisesRegex(LoadError, "t01 y t02"):
            self.load(FakeOdin())

    def test_main_stops_before_anything_when_owner_protection_fails(self):
        api = FakeOdin()
        with mock.patch.object(propietario, "proteger_banco", side_effect=propietario.PropietarioError("parada")), \
                mock.patch.object(cargar_mirador, "_http", api):
            self.assertEqual(cargar_mirador.main([]), 1)
        self.assertEqual(api.llamadas, [])


def write_data_with(datos, ruta_vieja, ruta_nueva):
    manifiesto = json.loads((datos / "manifiesto.json").read_text(encoding="utf-8"))
    for doc in manifiesto["documentos"]:
        if doc["ruta"] == ruta_vieja:
            doc["ruta"] = ruta_nueva
    (datos / "manifiesto.json").write_text(json.dumps(manifiesto), encoding="utf-8")
    (datos / "corpus" / ruta_nueva).write_bytes(b"x")


class MiradorConfigsTest(unittest.TestCase):
    def test_humo_config_takes_only_five_questions_and_a_cent_budget(self):
        cfg = load_config(BANCO / "configs" / "mirador-humo.toml", BANCO)
        self.assertEqual(cfg.archives, ("ficticio", "mirador"))
        self.assertEqual(cfg.batteries, ("mirador",))
        self.assertEqual(cfg.sample.include, ("M01", "M15", "M24", "M27", "M29"))
        self.assertEqual(cfg.sample.size, 5)
        self.assertEqual(cfg.budget_usd, 0.1)
        self.assertEqual([(s.approach, s.count, s.model) for s in cfg.searchers], [("main", 1, "haiku")])

    def test_full_config_runs_the_whole_battery_with_three_searchers(self):
        cfg = load_config(BANCO / "configs" / "mirador.toml", BANCO)
        self.assertEqual(cfg.batteries, ("mirador",))
        self.assertIsNone(cfg.sample)
        self.assertEqual(cfg.budget_usd, 1.0)
        self.assertEqual([(s.approach, s.count) for s in cfg.searchers], [("main", 3)])


class MiradorMakeTest(unittest.TestCase):
    def test_target_checks_owner_protection_then_loads(self):
        result = subprocess.run(["make", "-n", "-s", "banco-cargar-mirador"], cwd=REPO, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("banco/propietario.py && ", result.stdout)
        self.assertTrue(result.stdout.rstrip().endswith("python3 banco/cargar_mirador.py"))


if __name__ == "__main__":
    unittest.main()
