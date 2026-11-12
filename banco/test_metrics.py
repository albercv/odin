"""Pruebas de las métricas del informe: jueces, acuerdo entre buscadores, coste y latencia. Sin IA ni red."""
import unittest
from unittest import mock

from banco import metrics


def _final(item_id, correcta, answer=None):
    return {"id": item_id, "answer": answer, "correcta_script": correcta, "approach": "majorityVote",
            "coste_usd": 0.0, "tokens": None, "duracion_s": 0.0, "error": None}


def _verdict(item_id, jury, duracion=0.0):
    judge = {"model": "haiku", "verdict": {"correcta": jury}, "coste_usd": 0.0, "tokens": None, "duracion_s": duracion}
    return {"id": item_id, "judges": [judge], "jury": jury}


def _answer(text):
    return {"respuesta": text, "no_lo_se": False, "citas": [{"documentId": "d1", "cardId": None, "pagina": 1, "fragmento": text}]}


def _no_consta():
    return {"respuesta": "No consta.", "no_lo_se": True, "citas": []}


class JudgesTest(unittest.TestCase):
    def setUp(self):
        self.items = {f"Q{n}": {"id": f"Q{n}", "tipo": "t", "claves": [["x"]]} for n in range(1, 5)}

    def test_kappa_and_rates_null_with_single_script_class(self):
        finals = [_final(f"Q{n}", True, _answer("a")) for n in range(1, 5)]
        verdicts = [_verdict(f"Q{n}", True) for n in range(1, 5)]
        block = metrics.judges_block(self.items, finals, verdicts)
        self.assertIsNone(block["kappa"])
        self.assertIsNone(block["falseAcceptRate"])
        self.assertIsNone(block["falseRejectRate"])

    def test_kappa_and_rates_with_two_classes(self):
        script = [True, True, False, False]
        jury = [True, False, False, False]
        finals = [_final(f"Q{n}", s, _answer("a")) for n, s in enumerate(script, start=1)]
        verdicts = [_verdict(f"Q{n}", j) for n, j in enumerate(jury, start=1)]
        block = metrics.judges_block(self.items, finals, verdicts)
        self.assertAlmostEqual(block["kappa"], 0.5)
        self.assertEqual(block["falseAcceptRate"], 0.0)
        self.assertEqual(block["falseRejectRate"], 0.5)
        self.assertIsNone(block["ppi"])

    def test_missing_verdict_counts_as_undecided(self):
        finals = [_final("Q1", True, _answer("a")), _final("Q2", False, _answer("b"))]
        block = metrics.judges_block(self.items, finals, [_verdict("Q1", True)])
        self.assertEqual(block["undecided"], 1)


class NoConstaTest(unittest.TestCase):
    def setUp(self):
        self.items = {"Q1": {"id": "Q1", "tipo": "t", "claves": [["x"]]}, "Q2": {"id": "Q2", "tipo": "t", "claves": []},
                      "Q3": {"id": "Q3", "tipo": "t", "claves": []}}

    def test_no_consta_is_counted_and_unkeyed_ones_go_to_manual_review(self):
        finals = [_final("Q1", True, _answer("a")), _final("Q2", False, _no_consta()), _final("Q3", False, _no_consta())]
        block = metrics.judges_block(self.items, finals, [_verdict("Q1", True)])
        self.assertEqual(block["notJudgedNoConsta"], 2)
        self.assertEqual(block["manualReview"], ["Q2", "Q3"])
        self.assertEqual(block["undecided"], 2)

    def test_calibration_uses_only_answers_that_cite(self):
        items = {n: {"id": n, "tipo": "t", "claves": [["x"]]} for n in ("Q1", "Q2", "Q3")}
        finals = [_final("Q1", True, _answer("a")), _final("Q2", False, _answer("b")), _final("Q3", False, _no_consta())]
        verdicts = [_verdict("Q1", True), _verdict("Q2", False), _verdict("Q3", True)]  # un «no consta» nunca tiene jurado
        block = metrics.judges_block(items, finals, verdicts)
        self.assertEqual(block["falseAcceptRate"], 0.0)
        self.assertEqual(block["kappa"], 1.0)


class AgreementTest(unittest.TestCase):
    def test_groups_questions_by_agreeing_searchers(self):
        # la firma son los documentos citados: Q1 coincide en dos de tres; Q2, en los tres (sin citas)
        def cited(text, document):
            return {"respuesta": text, "no_lo_se": False, "citas": [{"documentId": document, "pagina": 1}]}
        runs = [
            {"id": "Q1", "approach": "main", "replica": r, "respuesta": cited(t, d), "error": None}
            for r, (t, d) in enumerate([("Cuesta 10 €.", "d1"), ("Cuesta 10 €.", "d1"), ("Cuesta 20 €.", "d2")])
        ] + [
            {"id": "Q2", "approach": "main", "replica": r, "respuesta": _answer(t), "error": None}
            for r, t in enumerate(["Son 1.", "Son 2.", "Son 3."])
        ]
        finals = [_final("Q1", True, _answer("a")), _final("Q2", False, _answer("b"))]
        self.assertEqual(metrics.agreement_block(finals, runs),
                         {"3": {"questions": 1, "accuracy": 0.0}, "2": {"questions": 1, "accuracy": 1.0}})


def _cited(text, document):
    return {"respuesta": text, "no_lo_se": False, "citas": [{"documentId": document, "pagina": 1}]}


def _valid_run(question, replica, text, document, error=None):
    return {"id": question, "approach": "main", "replica": replica, "respuesta": _cited(text, document), "error": error}


class AgreementRunsTest(unittest.TestCase):
    """Acuerdo por ejecución: «bien» es acierto del script; la firma es el documento citado."""

    ITEMS = {"Q1": {"id": "Q1"}, "Q2": {"id": "Q2"}}

    def _block(self, runs):
        with mock.patch("banco.scoring.is_correct", side_effect=lambda item, a, ctx: a is not None and a["respuesta"] == "bien"):
            return metrics.agreement_runs_block(self.ITEMS, runs, ctx=None)

    def _runs(self):
        return [_valid_run("Q1", 0, "bien", "d1"), _valid_run("Q1", 1, "bien", "d1"), _valid_run("Q1", 2, "mal", "d1"),
                _valid_run("Q1", 3, "mal", "d2"),
                _valid_run("Q2", 0, "bien", "d1"), _valid_run("Q2", 1, "mal", "d2")]

    def test_splits_runs_by_majority_and_counts_failures_outside_it(self):
        block = self._block(self._runs())
        self.assertEqual(block["inMajority"], {"runs": 3, "accuracy": 0.6667})
        self.assertEqual(block["outsideMajority"], {"runs": 3, "accuracy": 0.3333})
        self.assertEqual(block["failuresOutsideMajority"], 0.6667)  # 2 de los 3 fallos quedan fuera

    def test_agreement_level_splits_questions_at_sixty_percent(self):
        block = self._block(self._runs())
        # Q1: 3 de 4 coinciden (75 %), la mayoría acierta; Q2: empate, sin mayoría, la respuesta es «no consta»
        self.assertEqual(block["byLevel"], {"atLeast60": {"questions": 1, "accuracy": 1.0},
                                            "below60": {"questions": 1, "accuracy": 0.0}})

    def test_runs_with_error_are_not_counted(self):
        runs = [*self._runs(), _valid_run("Q1", 9, "mal", "d9", error="timeout")]
        self.assertEqual(self._block(runs), self._block(self._runs()))

    def test_without_valid_runs_rates_are_not_calculable(self):
        block = self._block([_valid_run("Q1", 0, "bien", "d1", error="x")])
        self.assertEqual(block["inMajority"], {"runs": 0, "accuracy": None})
        self.assertIsNone(block["failuresOutsideMajority"])


class AgreementReportTest(unittest.TestCase):
    def test_report_shows_run_level_agreement_first(self):
        from banco.report import render
        block = {"inMajority": {"runs": 3, "accuracy": 0.6667}, "outsideMajority": {"runs": 1, "accuracy": 0.0},
                 "failuresOutsideMajority": 0.5, "byLevel": {"atLeast60": {"questions": 2, "accuracy": 1.0},
                                                            "below60": {"questions": 0, "accuracy": None}}}
        text = render({"origen": {}, "agreementRuns": block, "cost": {"notifiedUsd": 0.0, "notifiedUsdFromRecords": 0.0,
                       "unknownCostRecords": 0, "strategyUsdPerAnswer": None, "tokensPerAnswer": None,
                       "searchers": {"tokensPerAnswer": None}, "combiner": {"tokensPerAnswer": None},
                       "judges": {"tokensPerAnswer": None}}})
        self.assertIn("Acierto de las ejecuciones que coinciden con la mayoría de su pregunta: 66,7 % (3 ejecuciones)", text)
        self.assertIn("Fallos que quedan fuera de la mayoría: 50,0 %", text)
        self.assertIn("al menos el 60 %: 100,0 % (2 preguntas); menos del 60 %: no calculable (0 preguntas)", text)


class CostTest(unittest.TestCase):
    def test_notified_cost_excludes_cached_runs(self):
        runs = [{"id": "Q1", "coste_usd": 0.001, "cached": False, "tokens": {"entrada": 10, "salida": 5}},
                {"id": "Q1", "coste_usd": 0.002, "cached": True, "tokens": {"entrada": 10, "salida": 5}}]
        finals = [_final("Q1", True, _answer("a"))]
        verdicts = [{"id": "Q1", "jury": True, "judges": [{"coste_usd": 0.0005, "tokens": {"entrada": 3}}]}]
        cost = metrics.cost_block(runs, finals, verdicts)
        self.assertEqual(cost["notifiedUsd"], 0.0015)
        self.assertEqual(cost["searchers"]["tokensPerAnswer"], 30.0)
        self.assertEqual(cost["judges"]["tokensPerAnswer"], 3.0)


class UnknownCostTest(unittest.TestCase):
    def test_unknown_cost_is_counted_not_zero(self):
        runs = [{"id": "Q1", "coste_usd": None, "cached": False, "tokens": None},
                {"id": "Q1", "coste_usd": 0.001, "cached": False, "tokens": None}]
        cost = metrics.cost_block(runs, [_final("Q1", True, _answer("a"))], [])
        self.assertEqual(cost["unknownCostRecords"], 1)
        self.assertEqual(cost["notifiedUsd"], 0.001)


class DenominatorTest(unittest.TestCase):
    """Por respuesta: los buscadores dividen entre las preguntas ejecutadas; redactor y jueces, entre las medidas."""

    def test_searcher_cost_divides_by_executed_questions_not_the_battery(self):
        runs = [{"id": "Q1", "coste_usd": 0.002, "cached": False, "tokens": None},
                {"id": "Q2", "coste_usd": 0.002, "cached": False, "tokens": None}]
        cost = metrics.cost_block(runs, [], [])
        self.assertEqual(cost["searchers"]["usdPerAnswer"], 0.002)

    def test_combiner_cost_divides_by_measured_answers(self):
        finals = [{**_final("Q1", True, _answer("a")), "coste_usd": 0.004}, {**_final("Q2", False), "coste_usd": 0.0}]
        cost = metrics.cost_block([], finals, [])
        self.assertEqual(cost["combiner"]["usdPerAnswer"], 0.004)


class ApproachDenominatorTest(unittest.TestCase):
    ITEMS = {"Q1": {"id": "Q1", "citas_resueltas": [{"doc": "a.txt", "paginas": [1]}]},
             "Q2": {"id": "Q2", "citas_resueltas": [{"doc": "a.txt", "paginas": [2]}]},
             "Q3": {"id": "Q3", "citas_resueltas": [{"doc": "a.txt", "paginas": [3]}]}}

    def test_recall_divides_by_measured_questions_with_expected_pages(self):
        runs = [{"id": "Q1", "approach": "main", "error": None, "respuesta": None,
                 "consultas": [{"vistos": [{"documentId": "d1", "pagina": 1}]}]}]
        with mock.patch.object(metrics.scoring, "is_correct", return_value=True):
            block = metrics.approach_block(self.ITEMS, runs, None, {"a.txt": "d1"})
        self.assertEqual(block["main"]["recall"], 1.0)

    def test_accuracy_excludes_errored_runs_and_counts_them_apart(self):
        runs = [{"id": "Q1", "approach": "main", "error": None, "respuesta": _answer("a"), "consultas": []},
                {"id": "Q2", "approach": "main", "error": "fallo", "respuesta": None, "consultas": []}]
        with mock.patch.object(metrics.scoring, "is_correct", side_effect=lambda i, a, c: a is not None):
            block = metrics.approach_block(self.ITEMS, runs, None, {"a.txt": "d1"})
        self.assertEqual(block["main"]["accuracy"], 1.0)
        self.assertEqual(block["main"]["errors"], 1)


class LatencyTest(unittest.TestCase):
    def test_sums_slowest_searcher_composer_and_judge(self):
        runs = [{"id": "Q1", "duracion_s": 2.0}, {"id": "Q1", "duracion_s": 5.0}]
        finals = [{**_final("Q1", True), "duracion_s": 1.0}]
        verdicts = [_verdict("Q1", True, duracion=3.0)]
        self.assertEqual(metrics.latency_block(runs, finals, verdicts), {"p50": 9.0, "p95": 9.0})


if __name__ == "__main__":
    unittest.main()
