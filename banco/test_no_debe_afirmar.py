"""Pruebas de la clave opcional `no_debe_afirmar` (criterio 9 de D-126): afirmaciones prohibidas que anulan un acierto."""
import unittest
from unittest import mock

from banco import metrics, report, scoring

CORRECT = {"correcta": True}
# Clave al estilo de la batería de Mirador (pregunta de la factura).
MIRADOR = {"id": "M1", "tipo": "t", "claves": [["factura"]],
           "no_debe_afirmar": ["desajuste", r"no\s+cuadra", "discrepancia", "falta una segunda factura"]}


def _answer(text):
    return {"respuesta": text, "no_lo_se": False, "citas": []}


def _is_correct(item, text):
    with mock.patch.object(scoring.puntuar, "score_item", return_value=CORRECT):
        return scoring.is_correct(item, _answer(text), None)


class ForbiddenClaimsTest(unittest.TestCase):
    def test_match_flips_a_correct_answer_to_failed(self):
        self.assertFalse(_is_correct(MIRADOR, "La factura es de 120 EUR, pero hay un desajuste."))

    def test_reason_names_the_pattern_that_matched(self):
        self.assertEqual(scoring.forbidden_hit(MIRADOR, _answer("Esto no   cuadra con el pedido.")), r"no\s+cuadra")

    def test_no_field_or_empty_list_changes_nothing(self):
        for item in ({"id": "M1", "claves": [["x"]]}, {**MIRADOR, "no_debe_afirmar": []}):
            self.assertTrue(_is_correct(item, "hay un desajuste"))
            self.assertIsNone(scoring.forbidden_hit(item, _answer("hay un desajuste")))

    def test_clean_answer_stays_correct(self):
        self.assertTrue(_is_correct(MIRADOR, "La factura consta en la página 2."))

    def test_case_and_accent_insensitive(self):
        item = {"id": "M1", "no_debe_afirmar": ["discrepancia", "DESAJUSTE"]}
        self.assertIsNotNone(scoring.forbidden_hit(item, _answer("Hay una DISCREPÁNCIA.")))
        self.assertIsNotNone(scoring.forbidden_hit(item, _answer("un desajuste")))

    def test_already_failed_answer_is_not_rescued_and_no_answer_is_failed(self):
        with mock.patch.object(scoring.puntuar, "score_item", return_value={"correcta": False}):
            self.assertFalse(scoring.is_correct(MIRADOR, _answer("limpia"), None))
        self.assertFalse(scoring.is_correct(MIRADOR, None, None))

    def test_salvo_exempts_a_sentence_that_also_matches(self):
        item = {**MIRADOR, "no_debe_afirmar_salvo": ["no consta"]}
        self.assertIsNone(scoring.forbidden_hit(item, _answer("No consta ningún desajuste. La factura es de 120.")))
        self.assertIsNotNone(scoring.forbidden_hit(item, _answer("No consta la fecha. Hay un desajuste.")))


class ReportTest(unittest.TestCase):
    def test_summary_and_report_show_question_and_pattern(self):
        finals = [{"id": "M1", "answer": _answer("hay una discrepancia")}, {"id": "M2", "answer": _answer("ok")}]
        items = {"M1": MIRADOR, "M2": {"id": "M2"}}
        block = metrics.forbidden_block(items, finals)
        self.assertEqual(block, [{"id": "M1", "patron": "discrepancia"}])
        text = "\n".join(report._forbidden_lines({"noDebeAfirmar": block}))
        self.assertIn("M1", text)
        self.assertIn("discrepancia", text)


if __name__ == "__main__":
    unittest.main()
