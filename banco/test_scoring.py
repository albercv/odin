"""Pruebas del contexto de puntuación: textos de página para los jueces, páginas esperadas y observadas."""
import json
import tempfile
import unittest
from pathlib import Path

from banco import scoring

DATA = Path(__file__).resolve().parent.parent / "experimentos" / "2026-11-12-archivo-ficticio" / "datos"
DOC = "ribera/c2-almendros/poliza-seguro-2024.txt"


class PageTextTest(unittest.TestCase):
    def setUp(self):
        self.ctx = scoring.build_context(DATA, {"doc_ruta": {"doc-1": DOC}, "ficha_f": {"card-1": "F001"}})

    def test_document_returns_cited_page(self):
        self.assertIn("150", scoring.page_text("doc-1", 2, self.ctx))

    def test_card_returns_its_manifest_text(self):
        self.assertIn("Aviso de vecino", scoring.page_text("card-1", None, self.ctx))

    def test_unknown_identifier(self):
        self.assertEqual(scoring.page_text("zz", 1, self.ctx), scoring.NOT_FOUND)


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
        ctx = scoring.build_context(DATA, {"doc_ruta": {}, "ficha_f": {}})
        self.assertFalse(scoring.is_correct({"id": "Q1", "pregunta": "?", "claves": [["x"]]}, None, ctx))


class ExpedientesContextTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.data = Path(tmp.name)
        (self.data / "corpus").mkdir()
        (self.data / "manifiesto.json").write_text(json.dumps({"semilla": 1, "empresas": [], "proyectos": [], "trabajadores": [],
                                                              "documentos": [], "fichas": []}), encoding="utf-8")
        (self.data / "claves.json").write_text("[]", encoding="utf-8")

    def write_expedientes(self, sustituciones):
        case = {"caso": "ribera-C1", "proyecto": None, "documentos": [], "compartidos": [], "ajenos": [],
                "formas_numero": [], "sustituciones": sustituciones}
        (self.data / "expedientes.json").write_text(json.dumps([case]), encoding="utf-8")

    def test_expediente_pages_join_the_corpus(self):
        fichero = self.data / "expedientes" / "ribera" / "proyecto" / "c1.txt"
        fichero.parent.mkdir(parents=True)
        fichero.write_text("=== Página 1 ===\nTexto del expediente\n", encoding="utf-8")
        ctx = scoring.build_context(self.data, {"doc_ruta": {}, "ficha_f": {}})
        self.assertEqual(ctx.corpus["ribera/proyecto/c1.txt"], {1: "Texto del expediente"})

    def test_expediente_substitutions_mark_the_old_document_superseded(self):
        self.write_expedientes([["ribera/proyecto/viejo.txt", "ribera/proyecto/nuevo.txt"]])
        ctx = scoring.build_context(self.data, {"doc_ruta": {}, "ficha_f": {}})
        self.assertIn("ribera/proyecto/viejo.txt", ctx.reales)
        self.assertNotIn("ribera/proyecto/nuevo.txt", ctx.reales)

    def test_without_expedientes_the_context_is_empty(self):
        ctx = scoring.build_context(self.data, {"doc_ruta": {}, "ficha_f": {}})
        self.assertEqual(ctx.corpus, {})
        self.assertEqual(ctx.reales, set())


class TextsContextTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.data = Path(tmp.name)
        (self.data / "corpus").mkdir()
        (self.data / "manifiesto.json").write_text(json.dumps({"semilla": 1, "empresas": [], "proyectos": [], "trabajadores": [],
                                                              "documentos": [], "fichas": []}), encoding="utf-8")
        (self.data / "claves.json").write_text("[]", encoding="utf-8")

    def test_textos_pages_join_the_corpus_with_integer_pages(self):
        (self.data / "textos.json").write_text(json.dumps({"mirador/a.txt": {"1": "Uno", "2": "Dos"}}), encoding="utf-8")
        ctx = scoring.build_context(self.data, {"doc_ruta": {}, "ficha_f": {}})
        self.assertEqual(ctx.corpus["mirador/a.txt"], {1: "Uno", 2: "Dos"})

    def test_without_textos_nothing_is_added(self):
        ctx = scoring.build_context(self.data, {"doc_ruta": {}, "ficha_f": {}})
        self.assertEqual(ctx.corpus, {})


if __name__ == "__main__":
    unittest.main()
