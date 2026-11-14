"""Pruebas de la comprobación previa (D-094): reglas, datos, capacidades, herramientas y ejecutor, sin IA y sin red.
Odín se finge con FakeOdin (API y MCP por JSON-RPC); los datos son los reales del experimento, solo de lectura."""
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from banco import comprobacion
from banco.comprobacion import ComprobacionError
from banco.config import CAPABILITIES, load_config
from banco.layout import Layout
from banco.medir_path import ejecutar

BANCO = Path(__file__).resolve().parent
EXP = BANCO.parent / "experimentos" / "2026-11-12-archivo-ficticio"
API = "http://127.0.0.1:48080"
MCP = "http://127.0.0.1:48090/mcp"
MANIFIESTO = json.loads((EXP / "datos" / "manifiesto.json").read_text(encoding="utf-8"))
ITEMS = json.loads((EXP / "datos" / "bateria.json").read_text(encoding="utf-8"))
ACTORES = sorted({item["actor"] for item in ITEMS})
HERRAMIENTAS_ODIN = [*ejecutar.HERRAMIENTAS, "saveKnowledgeCard"]

TOML = """name = "prueba"
archives = ["ficticio"]
battery = "desarrollo"
mode = "explore"
repetitions = 1
budgetUsd = {budget}
concurrency = 1
{extra}
[[searchers]]
approach = "main"
count = 2
model = "haiku"

[combiner]
kind = "single"

[[judges]]
rubric = "strict"
count = 1
model = "haiku"

[judging]
aggregation = "majority"
"""


class FakeOdin:
    """Odín falso: GET /v1/me y MCP en JSON-RPC (respuesta JSON o SSE), con lo que se le dé como capacidades,
    herramientas y claves rechazadas."""

    def __init__(self, caps=CAPABILITIES, herramientas=HERRAMIENTAS_ODIN, rechazadas=(), sse=False):
        self.caps, self.herramientas, self.rechazadas, self.sse = list(caps), list(herramientas), set(rechazadas), sse
        self.llamadas = []

    def __call__(self, metodo, url, cabeceras, cuerpo=None):
        self.llamadas.append((metodo, url))
        if cabeceras.get("Authorization", "").removeprefix("Bearer ") in self.rechazadas:
            return 401, {}, ""
        if metodo == "GET":
            return 200, {}, json.dumps({"company": {"capabilities": self.caps}})
        if metodo == "DELETE":
            return 200, {}, ""
        if cuerpo["method"] == "initialize":
            return 200, {"mcp-session-id": "sesion-1"}, self._out(cuerpo, {"protocolVersion": "2025-03-26"})
        if cuerpo["method"] == "notifications/initialized":
            return 202, {}, ""
        if cabeceras.get("mcp-session-id") != "sesion-1":
            return 400, {}, "Sesión MCP no válida o no iniciada."
        return 200, {}, self._out(cuerpo, {"tools": [{"name": t} for t in self.herramientas]})

    def _out(self, mensaje, resultado):
        cuerpo = json.dumps({"jsonrpc": "2.0", "id": mensaje["id"], "result": resultado})
        return f"event: message\ndata: {cuerpo}\n\n" if self.sse else cuerpo


def carga_base():
    return {"api": API, "mcp": MCP, "claves": {a: f"clave-{a}" for a in ACTORES},
            "doc_ruta": {f"doc-{n}": d["ruta"] for n, d in enumerate(MANIFIESTO["documentos"])},
            "ficha_f": {f"card-{n}": f["id"] for n, f in enumerate(MANIFIESTO["fichas"])}}


class Base(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.tmp = Path(tmp.name)
        self.layout = Layout(banco=BANCO, exp=EXP, state_dir=self.tmp / "estado", results_dir=self.tmp / "resultados",
                             cache=self.tmp / "cache", repo=BANCO.parent, claude_bin="no-usar",
                             claude_judge_bin="no-usar", ollama_url="http://127.0.0.1:1", timeout=30)
        self.layout.state_dir.mkdir()
        self.carga = carga_base()
        self.escribir_carga()

    def escribir_carga(self, **cambios):
        self.carga = {**self.carga, **cambios}
        (self.layout.state_dir / "carga.json").write_text(json.dumps(self.carga), encoding="utf-8")

    def cfg(self, budget=1.5, extra=""):
        path = self.tmp / "experimento.toml"
        path.write_text(TOML.format(budget=budget, extra=extra), encoding="utf-8")
        return load_config(path, BANCO)


class ReglasTest(Base):
    def test_budget_within_the_cap_passes(self):
        self.assertEqual(comprobacion.comprobar(self.cfg(budget=5.0), self.layout, http=FakeOdin())["reglas"], [])

    def test_budget_above_the_cap_without_approval_fails(self):
        mensajes = comprobacion.comprobar(self.cfg(budget=5.5), self.layout, http=FakeOdin())["reglas"]
        self.assertEqual(len(mensajes), 1)
        self.assertIn("5.5", mensajes[0])
        self.assertIn("D-094", mensajes[0])

    def test_budget_above_the_cap_with_owner_approval_passes(self):
        cfg = self.cfg(budget=11.4, extra='ownerApproval = "D-077"\n')
        self.assertEqual(comprobacion.comprobar(cfg, self.layout, http=FakeOdin())["reglas"], [])

    def test_owner_approval_must_name_a_decision(self):
        from banco.config import ConfigError
        with self.assertRaisesRegex(ConfigError, "ownerApproval"):
            self.cfg(budget=11.4, extra='ownerApproval = "x"\n')

    def test_fewer_capabilities_need_owner_approval(self):
        sin = self.cfg(extra='capabilities = ["SEARCH_KNOWLEDGE"]\n')
        con = self.cfg(extra='capabilities = ["SEARCH_KNOWLEDGE"]\nownerApproval = "D-094"\n')
        self.assertTrue(any("capacidades" in m for m in comprobacion.reglas(sin)))
        self.assertEqual(comprobacion.reglas(con), [])

    def test_real_config_over_the_cap_is_refused(self):
        cfg = load_config(BANCO / "configs" / "barrido-40.toml", BANCO)
        self.assertTrue(comprobacion.comprobar(cfg, self.layout, http=FakeOdin())["reglas"])


class DatosTest(Base):
    def datos(self):
        return comprobacion.comprobar(self.cfg(), self.layout, http=FakeOdin())["datos"]

    def test_real_stack_and_battery_pass(self):
        self.assertEqual(self.datos(), [])

    def test_missing_carga_fails_closed(self):
        (self.layout.state_dir / "carga.json").unlink()
        secciones = comprobacion.comprobar(self.cfg(), self.layout, http=FakeOdin())
        self.assertIn("no hay carga", secciones["datos"][0])
        self.assertEqual(secciones["capacidades"], ["no se comprueba sin carga"])
        self.assertEqual(secciones["herramientas"], ["no se comprueba sin carga"])

    def test_actor_without_key_is_named(self):
        self.escribir_carga(claves={a: "x" for a in ACTORES if a != "t06"})
        self.assertIn("actores sin clave en la pila: t06", self.datos())

    def test_citation_document_not_loaded_is_named(self):
        self.escribir_carga(doc_ruta={})
        mensajes = self.datos()
        self.assertTrue(any("documentos de las preguntas que no están en la pila" in m for m in mensajes))

    def test_ficha_not_loaded_is_named(self):
        self.escribir_carga(ficha_f={})
        self.assertTrue(any("fichas de las preguntas" in m for m in self.datos()))

    def test_expediente_documents_are_refused_by_the_loaded_guard(self):
        cfg = self.cfg()
        cfg = type(cfg)(**{**cfg.__dict__, "batteries": ("generalidad-expediente",), "archives": ("ficticio",)})
        folder = self.tmp / "datos"
        folder.mkdir()
        (folder / "manifiesto.json").write_text(json.dumps({"documentos": [], "fichas": []}), encoding="utf-8")
        (folder / "expedientes.json").write_text(json.dumps([{"documentos": ["a/y.txt"], "compartidos": []}]), encoding="utf-8")
        with mock.patch("banco.capas.data_dir", return_value=folder):
            mensajes = comprobacion.comprobar(cfg, self.layout, http=FakeOdin())["datos"]
        self.assertTrue(any("EXPEDIENTES=1" in m for m in mensajes))


class CapacidadesTest(Base):
    def capacidades(self, http, **cambios):
        cfg = self.cfg(**cambios)
        return comprobacion.comprobar(cfg, self.layout, http=http)["capacidades"]

    def test_all_capabilities_pass(self):
        self.assertEqual(self.capacidades(FakeOdin()), [])

    def test_missing_capability_is_named_per_actor(self):
        mensajes = self.capacidades(FakeOdin(caps=["SEARCH_KNOWLEDGE", "SAVE_KNOWLEDGE_CARD"]))
        self.assertEqual(len(mensajes), len(ACTORES))
        self.assertIn("SIMILAR_CASES", mensajes[0])

    def test_config_may_ask_for_fewer_capabilities(self):
        mensajes = self.capacidades(FakeOdin(caps=["SEARCH_KNOWLEDGE"]), extra='capabilities = ["SEARCH_KNOWLEDGE"]\n')
        self.assertEqual(mensajes, [])

    def test_rejected_key_is_reported(self):
        mensajes = self.capacidades(FakeOdin(rechazadas={"clave-t02"}))
        self.assertEqual(mensajes, ["t02: GET /v1/me devolvió 401, se esperaba 200"])


class HerramientasTest(Base):
    def herramientas(self, http, **cambios):
        return comprobacion.comprobar(self.cfg(**cambios), self.layout, http=http)["herramientas"]

    def test_all_tools_offered_pass_over_json(self):
        self.assertEqual(self.herramientas(FakeOdin()), [])

    def test_all_tools_offered_pass_over_sse(self):
        self.assertEqual(self.herramientas(FakeOdin(sse=True)), [])

    def test_missing_tool_is_named(self):
        herramientas = [t for t in HERRAMIENTAS_ODIN if t != "findSimilarCases"]
        mensajes = self.herramientas(FakeOdin(herramientas=herramientas))
        self.assertEqual(len(mensajes), len(ACTORES))
        self.assertIn("findSimilarCases", mensajes[0])

    def test_tool_of_a_capability_not_asked_is_not_expected(self):
        herramientas = [t for t in HERRAMIENTAS_ODIN if t != "findSimilarCases"]
        mensajes = self.herramientas(FakeOdin(herramientas=herramientas), extra='capabilities = ["SEARCH_KNOWLEDGE"]\n')
        self.assertEqual(mensajes, [])

    def test_no_expected_tool_fails(self):
        mensajes = self.herramientas(FakeOdin(), extra='capabilities = ["SIMILAR_CASES"]\nownerApproval = "D-094"\n')
        self.assertEqual(len(mensajes), 1)
        self.assertIn("ninguna herramienta", mensajes[0])

    def test_session_is_opened_with_header_and_closed(self):
        http = FakeOdin()
        self.herramientas(http)
        self.assertIn(("DELETE", MCP), http.llamadas)

    def test_every_tool_of_the_bench_has_a_capability_rule(self):
        self.assertEqual([t for t in ejecutar.HERRAMIENTAS if t not in comprobacion.REQUIEREN], [])

    def test_expected_tools_follow_the_capabilities(self):
        self.assertIn("findSimilarCases", comprobacion.herramientas_esperadas(CAPABILITIES))
        self.assertNotIn("findSimilarCases", comprobacion.herramientas_esperadas(["SIMILAR_CASES"]))
        self.assertNotIn("listMyKnowledgeCards", comprobacion.herramientas_esperadas(["SEARCH_KNOWLEDGE"]))


class EjecutorTest(unittest.TestCase):
    def test_current_executor_reads_every_odd_event(self):
        self.assertEqual(comprobacion.fallos_ejecutor(), [])

    def test_a_broken_executor_is_reported_case_by_case(self):
        with mock.patch("banco.medir_path.ejecutar.leer_consultas", side_effect=AttributeError("'str' object has no attribute 'get'")):
            fallos = comprobacion.fallos_ejecutor()
        self.assertTrue(fallos)
        self.assertTrue(all("el ejecutor falla con" in f for f in fallos))

    def test_passing_executor_has_no_failures(self):
        with mock.patch("banco.medir_path.ejecutar.leer_consultas", return_value=[]):
            self.assertEqual(comprobacion.fallos_ejecutor(), [])

    def test_non_json_and_empty_lines_never_fail(self):
        self.assertEqual(comprobacion.fallos_ejecutor(["", "no es json {", "[1, 2]"]), [])

    def test_failure_names_the_line_when_the_event_has_no_label(self):
        with mock.patch("banco.medir_path.ejecutar.leer_consultas", side_effect=KeyError("name")):
            self.assertEqual(comprobacion.fallos_ejecutor(['{"type": "x"}']),
                             ["el ejecutor falla con la línea 1: 'name'"])


class RedireccionTest(unittest.TestCase):
    def test_redirect_is_not_followed_so_the_key_never_leaves(self):
        import http.server, threading
        visitas = []

        class Destino(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                visitas.append(self.headers.get("Authorization"))
                self.send_response(200); self.end_headers(); self.wfile.write(b"{}")

            def log_message(self, *a):
                pass

        destino = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Destino)

        class Origen(Destino):
            def do_GET(self):
                self.send_response(302); self.send_header("Location", f"http://127.0.0.1:{destino.server_port}/"); self.end_headers()

        origen = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Origen)
        for s in (origen, destino):
            threading.Thread(target=s.serve_forever, daemon=True).start()
        self.addCleanup(lambda: [s.shutdown() for s in (origen, destino)])
        estado, _, _ = comprobacion._http("GET", f"http://127.0.0.1:{origen.server_port}/v1/me", {"Authorization": "Bearer k"})
        self.assertEqual(estado, 302)
        self.assertEqual(visitas, [])


class CheckAndReportTest(Base):
    def test_check_flattens_the_sections(self):
        with mock.patch("banco.medir_path.ejecutar.leer_consultas", side_effect=AttributeError("roto")):
            mensajes = comprobacion.check(self.cfg(budget=9), self.layout, http=FakeOdin())
        self.assertTrue(any("D-094" in m for m in mensajes))
        self.assertIn("el ejecutor falla con", " ".join(mensajes))

    def test_previa_writes_the_report_with_config_sha(self):
        path = self.tmp / "experimento.toml"
        path.write_text(TOML.format(budget=1.5, extra=""), encoding="utf-8")
        informe = comprobacion.previa(path, self.layout, http=FakeOdin())
        guardado = json.loads((self.layout.state_dir / "comprobacion-prueba.json").read_text(encoding="utf-8"))
        self.assertEqual(guardado, informe)
        self.assertEqual(len(informe["config_sha256"]), 64)
        self.assertEqual(set(informe["comprobaciones"]),
                         {"propietario", "reglas", "datos", "capacidades", "herramientas", "ejecutor"})

    def test_exigir_refuses_a_failed_report(self):
        informe = {"fallos": ["presupuesto de 9 USD"]}
        with self.assertRaisesRegex(ComprobacionError, r"^comprobación previa fallida \(D-094\): presupuesto"):
            comprobacion.exigir(informe)

    def test_exigir_lets_a_passing_report_through(self):
        self.assertEqual(comprobacion.exigir({"fallos": []}), {"fallos": []})

    def test_guardar_en_copies_the_report_to_the_results_folder(self):
        folder = self.tmp / "carpeta"
        folder.mkdir()
        comprobacion.guardar_en({"fallos": []}, folder)
        self.assertEqual(json.loads((folder / "COMPROBACION.json").read_text(encoding="utf-8")), {"fallos": []})

    def test_real_http_refuses_non_local_hosts(self):
        with self.assertRaises(OSError):
            comprobacion._http("GET", "http://example.com/v1/me", {})

    def test_whole_check_on_real_data_passes(self):
        self.assertEqual(comprobacion.check(self.cfg(), self.layout, http=FakeOdin()), [])

    def test_cli_comprobar_exits_one_with_the_report(self):
        from banco import banco
        path = self.tmp / "experimento.toml"
        path.write_text(TOML.format(budget=9, extra=""), encoding="utf-8")
        with mock.patch("banco.comprobacion._http", FakeOdin()), mock.patch("sys.stdout") as out:
            code = banco.main(["comprobar", "--config", str(path)], layout=self.layout)
        self.assertEqual(code, 1)
        self.assertIn("D-094", "".join(c.args[0] for c in out.write.call_args_list))


class PropietarioTest(Base):
    """D-099: la comprobación previa no habla con la instalación del propietario (8080, 8088, 8090)."""

    def test_stack_on_an_owner_port_fails_first_and_sends_nothing(self):
        self.escribir_carga(mcp="http://127.0.0.1:8090/mcp")
        fake = FakeOdin()
        with mock.patch.dict(os.environ, {}, clear=True):
            secciones = comprobacion.comprobar(self.cfg(), self.layout, http=fake)
        self.assertEqual(list(secciones)[0], "propietario")
        self.assertIn("No se ha cambiado nada.", secciones["propietario"][0])
        self.assertEqual(fake.llamadas, [])
        self.assertEqual(secciones["herramientas"], [comprobacion.NO_SE_COMPRUEBA_PROPIETARIO])

    def test_stack_on_bench_ports_passes_the_section(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            self.assertEqual(comprobacion.comprobar(self.cfg(), self.layout, http=FakeOdin())["propietario"], [])

    def test_unreadable_carga_fails_closed_with_the_same_message_ending(self):
        (self.layout.state_dir / "carga.json").write_text("{no es json", encoding="utf-8")
        with mock.patch.dict(os.environ, {}, clear=True):
            mensajes = comprobacion.comprobar(self.cfg(), self.layout, http=FakeOdin())["propietario"]
        self.assertTrue(mensajes[0].endswith("No se ha cambiado nada."))


if __name__ == "__main__":
    unittest.main()
