"""Pruebas del redactor: textos de los enfoques composer y pageFusion, y la llamada al ejecutor con un claude falso."""
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

from banco.compose import composer_text, compose, page_fusion_text
from banco.medir_path import ejecutar
from banco.pool import PoolSettings

FAKE_CLAUDE = str(Path(__file__).resolve().parent.parent / "experimentos" / "2026-11-12-archivo-ficticio" / "medir" / "fake_claude.py")
ITEM = {"id": "Q001", "actor": "t01", "pregunta": "¿Cuánto cuesta?"}
CARGA = {"url_mcp": "http://127.0.0.1:1/", "claves": {"t01": "k1"}, "doc_ruta": {}, "ficha_f": {}}
SETTINGS = PoolSettings(claude_bin=FAKE_CLAUDE, timeout=30, concurrency=1, budget_usd=1.0, max_turns=None, odin_commit="abc")


def _run(approach, replica, texto, documento, pagina, error=None):
    respuesta = {"respuesta": texto, "no_lo_se": False, "citas": [{"documentId": documento, "cardId": None, "pagina": pagina, "fragmento": texto}]}
    return {"approach": approach, "replica": replica, "error": error, "respuesta": respuesta,
            "consultas": [{"vistos": [{"documentId": documento, "pagina": pagina}]}]}


RUNS = [_run("main", 0, "Cuesta 10 €", "d1", 2), _run("topic", 0, "Cuesta 12 €", "d2", 5),
        _run("main", 1, "Cuesta 99 €", "d9", 1, error="fallo del servidor")]


class ComposerTextTest(unittest.TestCase):
    def test_composer_prompt_contains_all_answers_and_rule(self):
        texto = composer_text(RUNS)
        self.assertIn("Cuesta 10 €", texto)
        self.assertIn("Cuesta 12 €", texto)
        self.assertNotIn("Cuesta 99", texto)  # las respuestas con error no cuentan
        self.assertIn("verifyCitation", texto)
        self.assertIn("«no consta»", texto)


class PageFusionTextTest(unittest.TestCase):
    def test_page_fusion_prompt_contains_rrf_pages_only(self):
        texto = page_fusion_text([("d1", 2), ("d2", 5)])
        self.assertIn("documentId d1, página 2", texto)
        self.assertIn("documentId d2, página 5", texto)
        self.assertIn("readPage", texto)
        self.assertNotIn("Cuesta", texto)


class ComposeTest(unittest.TestCase):
    def test_unknown_kind_is_rejected(self):
        with self.assertRaises(ValueError):
            compose(ITEM, RUNS, CARGA, SETTINGS, "otro", "haiku")

    def test_composer_runs_through_executor_with_model_and_approach(self):
        with mock.patch.object(ejecutar, "responder", return_value={"respuesta": None, "error": "x"}) as responder:
            registro = compose(ITEM, RUNS, CARGA, SETTINGS, "composer", "haiku")
        enfoque = responder.call_args.args[4]
        self.assertIn("Cuesta 10 €", enfoque)
        self.assertEqual(responder.call_args.args[2].modelo, "haiku")
        self.assertEqual(registro["approach"], "composer")

    def test_page_fusion_without_pages_uses_rrf_pages(self):
        with mock.patch.object(ejecutar, "responder", return_value={"respuesta": None, "error": "x"}) as responder:
            registro = compose(ITEM, RUNS, CARGA, SETTINGS, "pageFusion", "haiku")
        enfoque = responder.call_args.args[4]
        self.assertIn("documentId d1, página 2", enfoque)
        self.assertIn("documentId d2, página 5", enfoque)
        self.assertNotIn("Cuesta", enfoque)
        self.assertEqual(registro["approach"], "pageFusion")

    def test_composer_with_fake_claude_returns_record(self):
        registro = compose(ITEM, RUNS, CARGA, SETTINGS, "composer", "haiku")
        self.assertIsNone(registro["error"])
        self.assertEqual(registro["approach"], "composer")
        self.assertIn("eco clave=k1", registro["respuesta"]["respuesta"])


if __name__ == "__main__":
    unittest.main()
