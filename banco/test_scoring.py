"""Pruebas del contexto de puntuación: textos de página para los jueces, páginas esperadas y observadas."""
import unittest
from pathlib import Path

from banco import scoring

EXP = Path(__file__).resolve().parent.parent / "experimentos" / "2026-11-12-archivo-ficticio"
DOC = "ribera/c2-almendros/poliza-seguro-2024.txt"


class PageTextTest(unittest.TestCase):
    def setUp(self):
        self.ctx = scoring.build_context(EXP, {"doc_ruta": {"doc-1": DOC}, "ficha_f": {"card-1": "F001"}})

    def test_document_returns_cited_page(self):
        self.assertIn("150", scoring.page_text("doc-1", 2, self.ctx))

    def test_card_returns_its_manifest_text(self):
        self.assertIn("Aviso de vecino", scoring.page_text("card-1", None, self.ctx))

    def test_unknown_identifier(self):
        self.assertEqual(scoring.page_text("zz", 1, self.ctx), scoring.NO_ENCONTRADO)


class PagesTest(unittest.TestCase):
    def test_expected_pages_with_document_id(self):
        item = {"citas_resueltas": [{"doc": DOC, "paginas": [2, 3], "ancla": "x"}, {"doc": "otro.txt", "paginas": [1]}]}
        self.assertEqual(scoring.expected_pages(item, {DOC: "doc-1"}), {("doc-1", 2), ("doc-1", 3)})

    def test_observed_pages_from_citations_and_views(self):
        run = {"respuesta": {"citas": [{"documentId": "doc-1", "pagina": 2}]},
               "consultas": [{"vistos": [{"documentId": "doc-1", "pagina": 3}]}]}
        self.assertEqual(scoring.observed_pages(run), {("doc-1", 2), ("doc-1", 3)})

    def test_run_without_answer_observes_no_pages(self):
        self.assertEqual(scoring.observed_pages({"respuesta": None, "consultas": []}), set())

    def test_no_answer_is_not_correct(self):
        ctx = scoring.build_context(EXP, {"doc_ruta": {}, "ficha_f": {}})
        self.assertFalse(scoring.is_correct({"id": "Q1", "pregunta": "?", "claves": [["x"]]}, None, ctx))


if __name__ == "__main__":
    unittest.main()
