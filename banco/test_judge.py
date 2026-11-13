"""Pruebas de los jueces (claude -p con Haiku y Ollama falso en 127.0.0.1) y de la agregación de veredictos."""
import contextlib
import os
import random
import unittest
from pathlib import Path
from unittest import mock

from banco.fakes.fake_ollama import start_fake_ollama
from banco.judge import aggregate, check_judge_init, is_no_consta, judge_material, run_claude_judge, run_ollama_judge
from banco.medir_path import ejecutar

FAKE_JUDGE = str(Path(__file__).resolve().parent / "fakes" / "fake_judge.py")
ITEM = {"id": "Q001", "actor": "t01", "pregunta": "¿Cuánto cuesta el mantenimiento?"}
ANSWER = {"respuesta": "Cuesta 10 €.", "no_lo_se": False, "citas": [
    {"documentId": "d1", "cardId": None, "pagina": 2, "fragmento": "10 €"},
    {"documentId": "d2", "cardId": None, "pagina": 5, "fragmento": "mantenimiento"},
    {"documentId": "d3", "cardId": None, "pagina": 7, "fragmento": "otro"},
    {"documentId": None, "cardId": "c1", "pagina": None, "fragmento": "ficha"}]}
VALID_YES = {"correcta": True, "respaldada": True, "datosSinRespaldo": [], "motivo": "La respuesta coincide con las páginas."}
VALID_NO = {"correcta": False, "respaldada": False, "datosSinRespaldo": ["12 €"], "motivo": "La cifra no aparece en las páginas."}
INVALID = {"invalido": True, "motivo": "sin veredicto"}
RUBRIC = "Eres un juez."


def page_text(ident, page):
    return f"Texto de {ident} {page}"


@contextlib.contextmanager
def fake_env(**env):
    """Entorno sin variables FAKE_ heredadas, más las indicadas."""
    base = {k: v for k, v in os.environ.items() if not k.startswith("FAKE_")}
    with mock.patch.dict(os.environ, base, clear=True):
        os.environ.update(env)
        yield


class JudgeMaterialTest(unittest.TestCase):
    def test_judge_material_shuffles_pages_with_seed(self):
        a = judge_material(ITEM, ANSWER, page_text, random.Random(1))
        b = judge_material(ITEM, ANSWER, page_text, random.Random(2))
        c = judge_material(ITEM, ANSWER, page_text, random.Random(1))
        self.assertNotEqual(a, b)
        self.assertEqual(a, c)

    def test_judge_material_has_no_key_and_no_config_name(self):
        text = judge_material(ITEM, ANSWER, page_text, random.Random(1))
        for forbidden in ("claves", "ancla", "archivo-ficticio", "archivo ficticio", "Q001", "t01"):
            self.assertNotIn(forbidden, text)
        self.assertIn("Texto de c1", text)


class ClaudeJudgeTest(unittest.TestCase):
    def _judge(self, timeout=30):
        return run_claude_judge("material", RUBRIC, "haiku", FAKE_JUDGE, timeout)

    def test_claude_judge_isolation(self):
        with fake_env(FAKE_VEREDICTO="tools_extra"):
            with self.assertRaises(ejecutar.Aislamiento):
                self._judge()

    def test_missing_init_field_is_isolation_error(self):
        for mode in ("sin_tools", "sin_mcp", "sin_plugins"):
            with self.subTest(mode=mode), fake_env(FAKE_VEREDICTO=mode):
                with self.assertRaises(ejecutar.Aislamiento):
                    self._judge()

    def test_check_init_requires_all_fields_and_rejects_foreign_ones(self):
        builtin = {"name": "b", "source": "b@builtin"}
        bad_inits = [
            {"mcp_servers": [], "plugins": []},
            {"tools": ["StructuredOutput"], "plugins": []},
            {"tools": ["StructuredOutput"], "mcp_servers": []},
            {"tools": "StructuredOutput", "mcp_servers": [], "plugins": []},
            {"tools": ["StructuredOutput"], "mcp_servers": [{"name": "x"}], "plugins": []},
            {"tools": ["StructuredOutput"], "mcp_servers": [], "plugins": [{"name": "s", "source": "s@mercado"}]},
            {"tools": ["StructuredOutput", "Bash"], "mcp_servers": [], "plugins": [builtin]},
        ]
        for init in bad_inits:
            with self.subTest(init=init), self.assertRaises(ejecutar.Aislamiento):
                check_judge_init(init)
        check_judge_init({"tools": ["StructuredOutput"], "mcp_servers": [], "plugins": [builtin]})

    def test_ok_returns_verdict_cost_and_tokens(self):
        with fake_env(FAKE_VEREDICTO="si"):
            record = self._judge()
        self.assertEqual(record["verdict"], VALID_YES)
        self.assertEqual(record["model"], "haiku")
        self.assertEqual(record["coste_usd"], 0.0005)
        self.assertEqual(record["tokens"]["entrada"], 120)

    def test_quota_error_text_is_kept_in_record(self):
        with fake_env(FAKE_VEREDICTO="limite"):
            record = self._judge()
        self.assertTrue(record["verdict"]["invalido"])
        self.assertEqual(record["error"], "Claude AI usage limit reached")

    def test_ok_record_has_no_error(self):
        with fake_env(FAKE_VEREDICTO="si"):
            self.assertIsNone(self._judge()["error"])

    def test_invalid_structured_output_is_marked_invalid(self):
        with fake_env(FAKE_VEREDICTO="invalido"):
            self.assertTrue(self._judge()["verdict"]["invalido"])

    def test_result_without_structured_output_is_invalid(self):
        with fake_env(FAKE_VEREDICTO="sin_salida"):
            self.assertTrue(self._judge()["verdict"]["invalido"])

    def test_missing_init_event_is_invalid(self):
        with fake_env(FAKE_VEREDICTO="sin_init"):
            self.assertTrue(self._judge()["verdict"]["invalido"])

    def test_timeout_is_invalid_verdict(self):
        with fake_env(FAKE_VEREDICTO="si", FAKE_SLEEP="5"):
            self.assertTrue(self._judge(timeout=1)["verdict"]["invalido"])


class OllamaJudgeTest(unittest.TestCase):
    def test_ollama_judge_ok_is_free_and_returns_verdict(self):
        url, stop = start_fake_ollama("ok")
        self.addCleanup(stop)
        record = run_ollama_judge("material", RUBRIC, "qwen", url, 30)
        self.assertEqual(record["verdict"], VALID_YES)
        self.assertEqual(record["coste_usd"], 0)
        self.assertEqual(record["tokens"], {"entrada": 11, "salida": 7})
        self.assertEqual(record["duracion_s"], 2.0)

    def test_ollama_judge_no_json_and_missing_fields_are_invalid(self):
        for mode in ("no_json", "sin_campos"):
            with self.subTest(mode=mode):
                url, stop = start_fake_ollama(mode)
                self.addCleanup(stop)
                record = run_ollama_judge("material", RUBRIC, "qwen", url, 30)
                self.assertTrue(record["verdict"]["invalido"])

    def test_null_or_missing_metrics_do_not_raise(self):
        url, stop = start_fake_ollama("sin_metricas")
        self.addCleanup(stop)
        record = run_ollama_judge("material", RUBRIC, "qwen", url, 30)
        self.assertEqual(record["verdict"], VALID_YES)
        self.assertEqual(record["duracion_s"], 0.0)
        self.assertEqual(record["tokens"], {"entrada": None, "salida": None})

    def test_non_numeric_duration_counts_as_zero(self):
        url, stop = start_fake_ollama("duracion_texto")
        self.addCleanup(stop)
        record = run_ollama_judge("material", RUBRIC, "qwen", url, 30)
        self.assertEqual(record["verdict"], VALID_YES)
        self.assertEqual(record["duracion_s"], 0.0)

    def test_refuses_non_local_host_before_any_request(self):
        for url in ("http://ejemplo.com:11434", "http://127.0.0.1@ejemplo.com:11434"):
            with self.subTest(url=url), mock.patch("banco.judge.urllib.request.urlopen") as urlopen:
                with self.assertRaises(ValueError):
                    run_ollama_judge("material", RUBRIC, "qwen", url, 30)
                urlopen.assert_not_called()

    def test_ollama_ignores_http_proxy_variables(self):
        """Ollama es local: un proxy del entorno (aquí, uno cerrado) no debe interceptar la llamada."""
        url, stop = start_fake_ollama("ok")
        self.addCleanup(stop)
        with fake_env(HTTP_PROXY="http://127.0.0.1:9", http_proxy="http://127.0.0.1:9", NO_PROXY="", no_proxy=""):
            record = run_ollama_judge("material", RUBRIC, "ollama:qwen3.5:9b", url, 30)
        self.assertEqual(record["verdict"], VALID_YES)

    def test_request_sends_think_false_by_default(self):
        received = []
        url, stop = start_fake_ollama("ok", received)
        self.addCleanup(stop)
        run_ollama_judge("material", RUBRIC, "ollama:qwen", url, 30)
        self.assertIs(received[0]["think"], False)

    def test_request_sends_think_true_when_configured(self):
        received = []
        url, stop = start_fake_ollama("ok", received)
        self.addCleanup(stop)
        run_ollama_judge("material", RUBRIC, "ollama:qwen", url, 30, think=True)
        self.assertIs(received[0]["think"], True)

    def test_ollama_prefix_is_stripped_before_request(self):
        received = []
        url, stop = start_fake_ollama("ok", received)
        self.addCleanup(stop)
        record = run_ollama_judge("material", RUBRIC, "ollama:qwen", url, 30)
        self.assertEqual(received[0]["model"], "qwen")
        self.assertEqual(record["model"], "qwen")


class NoConstaTest(unittest.TestCase):
    def test_abstention_or_no_citations_is_no_consta(self):
        self.assertTrue(is_no_consta({"respuesta": "No consta.", "no_lo_se": True, "citas": []}))
        self.assertTrue(is_no_consta({"respuesta": "Cuesta 10 €.", "no_lo_se": False, "citas": []}))
        self.assertFalse(is_no_consta({"respuesta": "Cuesta 10 €.", "no_lo_se": False, "citas": [{"documentId": "d1"}]}))


class AggregateTest(unittest.TestCase):
    def test_invalid_verdicts_excluded_and_none_left_is_undecided(self):
        self.assertIsNone(aggregate([INVALID, INVALID], "majority"))
        self.assertTrue(aggregate([INVALID, VALID_YES], "unanimous"))

    def test_aggregate_rules(self):
        self.assertTrue(aggregate([VALID_YES, VALID_YES, VALID_YES, VALID_NO, VALID_NO], "majority"))
        self.assertFalse(aggregate([VALID_YES, VALID_YES, VALID_NO, VALID_NO], "majority"))  # empate es False
        self.assertFalse(aggregate([VALID_YES, VALID_YES, VALID_YES, VALID_NO], "unanimous"))
        self.assertFalse(aggregate([VALID_YES, VALID_YES, VALID_YES, VALID_NO, VALID_NO], "atLeast:4"))
        self.assertTrue(aggregate([VALID_YES, VALID_YES, VALID_YES, VALID_YES, VALID_NO], "atLeast:4"))
        with self.assertRaises(ValueError):
            aggregate([VALID_YES], "otra")


if __name__ == "__main__":
    unittest.main()
