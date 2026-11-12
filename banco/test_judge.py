"""Pruebas de los jueces (claude -p con Haiku y Ollama falso en 127.0.0.1) y de la agregación de veredictos."""
import contextlib
import os
import random
import unittest
from pathlib import Path
from unittest import mock

from banco.fakes.fake_ollama import start_fake_ollama
from banco.judge import aggregate, check_judge_init, judge_material, run_claude_judge, run_ollama_judge
from banco.medir_path import ejecutar

FAKE_JUDGE = str(Path(__file__).resolve().parent / "fakes" / "fake_judge.py")
ITEM = {"id": "Q001", "actor": "t01", "pregunta": "¿Cuánto cuesta el mantenimiento?"}
ANSWER = {"respuesta": "Cuesta 10 €.", "no_lo_se": False, "citas": [
    {"documentId": "d1", "cardId": None, "pagina": 2, "fragmento": "10 €"},
    {"documentId": "d2", "cardId": None, "pagina": 5, "fragmento": "mantenimiento"},
    {"documentId": "d3", "cardId": None, "pagina": 7, "fragmento": "otro"},
    {"documentId": None, "cardId": "c1", "pagina": None, "fragmento": "ficha"}]}
SI = {"correcta": True, "respaldada": True, "datosSinRespaldo": [], "motivo": "La respuesta coincide con las páginas."}
NO = {"correcta": False, "respaldada": False, "datosSinRespaldo": ["12 €"], "motivo": "La cifra no aparece en las páginas."}
INVALIDO = {"invalido": True, "motivo": "sin veredicto"}
RUBRICA = "Eres un juez."


def page_text(ident, pagina):
    return f"Texto de {ident} {pagina}"


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
        texto = judge_material(ITEM, ANSWER, page_text, random.Random(1))
        for prohibido in ("claves", "ancla", "archivo-ficticio", "archivo ficticio", "Q001", "t01"):
            self.assertNotIn(prohibido, texto)
        self.assertIn("Texto de c1", texto)


class ClaudeJudgeTest(unittest.TestCase):
    def test_isolation_rejects_extra_tool(self):
        with fake_env(FAKE_VEREDICTO="tools_extra"):
            with self.assertRaises(ejecutar.Aislamiento):
                run_claude_judge("material", RUBRICA, "haiku", FAKE_JUDGE, 30)

    def test_check_init_rejects_mcp_and_non_builtin_plugins(self):
        with self.assertRaises(ejecutar.Aislamiento):
            check_judge_init({"tools": ["StructuredOutput"], "mcp_servers": [{"name": "x"}], "plugins": []})
        with self.assertRaises(ejecutar.Aislamiento):
            check_judge_init({"tools": ["StructuredOutput"], "mcp_servers": [], "plugins": [{"name": "s", "source": "s@mercado"}]})

    def test_ok_returns_verdict_cost_and_tokens(self):
        with fake_env(FAKE_VEREDICTO="si"):
            registro = run_claude_judge("material", RUBRICA, "haiku", FAKE_JUDGE, 30)
        self.assertEqual(registro["verdict"], SI)
        self.assertEqual(registro["model"], "haiku")
        self.assertEqual(registro["coste_usd"], 0.0005)
        self.assertEqual(registro["tokens"]["entrada"], 120)

    def test_invalid_structured_output_is_marked_invalid(self):
        with fake_env(FAKE_VEREDICTO="invalido"):
            registro = run_claude_judge("material", RUBRICA, "haiku", FAKE_JUDGE, 30)
        self.assertTrue(registro["verdict"]["invalido"])

    def test_timeout_is_invalid_verdict(self):
        with fake_env(FAKE_VEREDICTO="si", FAKE_SLEEP="5"):
            registro = run_claude_judge("material", RUBRICA, "haiku", FAKE_JUDGE, 1)
        self.assertTrue(registro["verdict"]["invalido"])


class OllamaJudgeTest(unittest.TestCase):
    def test_ollama_judge_ok_is_free_and_returns_verdict(self):
        url, stop = start_fake_ollama("ok")
        self.addCleanup(stop)
        registro = run_ollama_judge("material", RUBRICA, "qwen", url, 30)
        self.assertEqual(registro["verdict"], SI)
        self.assertEqual(registro["coste_usd"], 0)
        self.assertEqual(registro["tokens"], {"entrada": 11, "salida": 7})

    def test_ollama_judge_no_json_and_missing_fields_are_invalid(self):
        for modo in ("no_json", "sin_campos"):
            with self.subTest(modo=modo):
                url, stop = start_fake_ollama(modo)
                self.addCleanup(stop)
                registro = run_ollama_judge("material", RUBRICA, "qwen", url, 30)
                self.assertTrue(registro["verdict"]["invalido"])


class AggregateTest(unittest.TestCase):
    def test_invalid_verdicts_excluded_and_none_left_is_undecided(self):
        self.assertIsNone(aggregate([INVALIDO, INVALIDO], "majority"))
        self.assertTrue(aggregate([INVALIDO, SI], "unanimous"))

    def test_aggregate_rules(self):
        self.assertTrue(aggregate([SI, SI, SI, NO, NO], "majority"))
        self.assertFalse(aggregate([SI, SI, NO, NO], "majority"))  # empate es False
        self.assertFalse(aggregate([SI, SI, SI, NO], "unanimous"))
        self.assertFalse(aggregate([SI, SI, SI, NO, NO], "atLeast:4"))
        self.assertTrue(aggregate([SI, SI, SI, SI, NO], "atLeast:4"))
        with self.assertRaises(ValueError):
            aggregate([SI], "otra")


if __name__ == "__main__":
    unittest.main()
