import ast
import unittest
from pathlib import Path

from banco.combine import answer_signature, majority_vote, rrf_pages, single


def _answer(texto, citas=(), no_lo_se=False):
    return {"respuesta": texto, "no_lo_se": no_lo_se, "citas": list(citas)}


def _cita(documento, pagina):
    return {"documentId": documento, "cardId": None, "pagina": pagina, "fragmento": "x"}


def _run(approach="main", replica=0, respuesta=None, error=None, consultas=()):
    return {
        "id": f"{approach}-{replica}",
        "approach": approach,
        "replica": replica,
        "cached": False,
        "respuesta": respuesta,
        "error": error,
        "consultas": list(consultas),
        "tokens": {"entrada": 0, "salida": 0, "cache_lectura": 0, "cache_creacion": 0},
        "coste_usd": 0.0,
        "duracion_s": 0.0,
    }


def _consulta(*vistos):
    return {"herramienta": "buscar", "argumentos": {}, "resultados": len(vistos), "vistos": list(vistos)}


class SignatureTest(unittest.TestCase):
    def test_signature_ignores_wording(self):
        a = _answer("La franquicia es de 300 €", citas=[_cita("d1", 1)])
        b = _answer("300 euros de franquicia", citas=[_cita("d1", 1)])
        self.assertEqual(answer_signature(a), answer_signature(b))

    def test_signature_ignores_cited_pages_and_numbers(self):
        self.assertEqual(
            answer_signature(_answer("300 €", citas=[_cita("d1", 1)])),
            answer_signature(_answer("500 €", citas=[_cita("d1", 2)])),
        )

    def test_signature_differs_on_cited_documents(self):
        self.assertNotEqual(
            answer_signature(_answer("no consta", citas=[_cita("d1", 1)])),
            answer_signature(_answer("no consta", citas=[_cita("d2", 1)])),
        )

    def test_signature_uses_cited_cards_too(self):
        card = {"documentId": None, "cardId": "c1", "pagina": None, "fragmento": ""}
        self.assertEqual(answer_signature(_answer("x", citas=[card])), answer_signature(_answer("y", citas=[dict(card)])))
        self.assertNotEqual(answer_signature(_answer("x", citas=[card])), answer_signature(_answer("x")))

    def test_signature_includes_no_lo_se(self):
        self.assertNotEqual(
            answer_signature(_answer("300 €")),
            answer_signature(_answer("300 €", no_lo_se=True)),
        )


class SingleTest(unittest.TestCase):
    def test_single_returns_main_replica_zero(self):
        runs = [
            _run("main", 1, _answer("otra")),
            _run("main", 0, _answer("la buena")),
            _run("otro", 0, _answer("ajena")),
        ]
        self.assertEqual(single(runs)["respuesta"], "la buena")

    def test_single_requires_main(self):
        with self.assertRaises(ValueError):
            single([_run("otro", 0, _answer("x"))])

    def test_single_returns_none_when_main_errored(self):
        self.assertIsNone(single([_run("main", 0, None, error="timeout")]))


class MajorityVoteTest(unittest.TestCase):
    def test_majority_picks_largest_group(self):
        runs = [
            _run("a", 0, _answer("300 €", citas=[_cita("d1", 1)])),
            _run("b", 0, _answer("500 €", citas=[_cita("d2", 1)])),
            _run("c", 0, _answer("euros: 300", citas=[_cita("d1", 3)])),
        ]
        self.assertEqual(majority_vote(runs)["respuesta"], "300 €")

    def test_majority_representative_has_most_citations(self):
        runs = [
            _run("a", 0, _answer("300 €", citas=[_cita("d1", 1)])),
            _run("b", 0, _answer("300 euros", citas=[_cita("d1", 1), _cita("d1", 1)])),  # mismas páginas, más citas
            _run("c", 0, _answer("500 €")),
        ]
        self.assertEqual(len(majority_vote(runs)["citas"]), 2)

    def test_majority_splits_answers_by_cited_documents_not_pages(self):
        runs = [
            _run("a", 0, _answer("no consta", citas=[_cita("d1", 1)])),
            _run("b", 0, _answer("no consta", citas=[_cita("d2", 1)])),
            _run("c", 0, _answer("no consta", citas=[_cita("d2", 9)])),
        ]
        self.assertEqual(majority_vote(runs)["citas"][0]["documentId"], "d2")

    def test_tie_returns_no_consta_with_trace(self):
        runs = [
            _run("a", 0, _answer("300 €", citas=[_cita("d1", 1)])),
            _run("b", 0, _answer("300 €", citas=[_cita("d1", 1)])),
            _run("c", 0, _answer("500 €")),
            _run("d", 0, _answer("500 €")),
        ]
        result = majority_vote(runs)
        self.assertTrue(result["no_lo_se"])
        self.assertEqual(result["citas"], [])
        self.assertIn("no coincidieron", result["respuesta"])

    def test_majority_ignores_errored_runs(self):
        runs = [
            _run("a", 0, None, error="timeout"),
            _run("b", 0, _answer("300 €")),
        ]
        self.assertEqual(majority_vote(runs)["respuesta"], "300 €")

    def test_majority_without_usable_runs_is_none(self):
        self.assertIsNone(majority_vote([_run("a", 0, None, error="x")]))


class RrfPagesTest(unittest.TestCase):
    def test_rrf_ranks_page_seen_by_more_runs_first(self):
        runs = [
            _run("a", 0, _answer("x", citas=[_cita("d1", 1)])),
            _run("b", 0, _answer("y", citas=[_cita("d2", 2)]), consultas=[_consulta({"documentId": "d1", "pagina": 1})]),
            _run("c", 0, _answer("z", citas=[_cita("d1", 1)])),
        ]
        self.assertEqual(rrf_pages(runs, top=2), [("d1", 1), ("d2", 2)])

    def test_rrf_ignores_runs_with_error(self):
        runs = [
            _run("a", 0, None, error="x", consultas=[_consulta({"documentId": "d9", "pagina": 9})]),
            _run("b", 0, _answer("y", citas=[_cita("d1", 1)])),
        ]
        self.assertEqual(rrf_pages(runs), [("d1", 1)])

    def test_rrf_skips_cards_and_dedupes_within_run(self):
        runs = [
            _run(
                "a",
                0,
                _answer("x", citas=[_cita("d1", 1), {"documentId": None, "cardId": "c1", "pagina": None, "fragmento": ""}]),
                consultas=[_consulta({"cardId": "c2"}, {"documentId": "d1", "pagina": 1}, {"documentId": "d1", "pagina": 3})],
            ),
        ]
        self.assertEqual(rrf_pages(runs), [("d1", 1), ("d1", 3)])

    def test_rrf_ties_keep_first_appearance(self):
        runs = [
            _run("a", 0, _answer("x", citas=[_cita("d2", 2)])),
            _run("b", 0, _answer("y", citas=[_cita("d1", 1)])),
        ]
        self.assertEqual(rrf_pages(runs), [("d2", 2), ("d1", 1)])


class EnglishIdentifiersTest(unittest.TestCase):
    """Identificadores en inglés en los dos módulos de combinación y puntuación (las claves JSON se quedan como están)."""
    SPANISH = {"cifras", "citas", "pares", "paginas", "vistos", "consulta", "ficha", "ruta", "pagina", "consta", "empate"}

    def test_no_spanish_identifiers_in_combine_and_scoring(self):
        for name in ("combine.py", "scoring.py"):
            tree = ast.parse((Path(__file__).resolve().parent / name).read_text(encoding="utf-8"))
            found = set()
            for node in ast.walk(tree):
                if isinstance(node, ast.Name):
                    found.add(node.id)
                elif isinstance(node, ast.arg):
                    found.add(node.arg)
                elif isinstance(node, (ast.FunctionDef, ast.ClassDef)):
                    found.add(node.name)
            flagged = sorted(n for n in found if set(n.lower().split("_")) & self.SPANISH)
            self.assertEqual(flagged, [], name)


if __name__ == "__main__":
    unittest.main()
