"""Pruebas de las métricas del informe: jueces, acuerdo entre buscadores, coste y latencia. Sin IA ni red."""
import unittest

from banco import metrics


def _final(item_id, correcta, answer=None):
    return {"id": item_id, "answer": answer, "correcta_script": correcta, "approach": "majorityVote",
            "coste_usd": 0.0, "tokens": None, "duracion_s": 0.0, "error": None}


def _verdict(item_id, jury, duracion=0.0):
    judge = {"model": "haiku", "verdict": {"correcta": jury}, "coste_usd": 0.0, "tokens": None, "duracion_s": duracion}
    return {"id": item_id, "judges": [judge], "jury": jury}


def _answer(text):
    return {"respuesta": text, "no_lo_se": False, "citas": []}


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


class AgreementTest(unittest.TestCase):
    def test_groups_questions_by_agreeing_searchers(self):
        runs = [
            {"id": "Q1", "approach": "main", "replica": r, "respuesta": _answer(t), "error": None}
            for r, t in enumerate(["Cuesta 10 €.", "Cuesta 10 €.", "Cuesta 20 €."])
        ] + [
            {"id": "Q2", "approach": "main", "replica": r, "respuesta": _answer(t), "error": None}
            for r, t in enumerate(["Son 1.", "Son 2.", "Son 3."])
        ]
        finals = [_final("Q1", True, _answer("a")), _final("Q2", False, _answer("b"))]
        self.assertEqual(metrics.agreement_block(finals, runs),
                         {"2": {"questions": 1, "accuracy": 1.0}, "1": {"questions": 1, "accuracy": 0.0}})


class CostTest(unittest.TestCase):
    def test_notified_cost_excludes_cached_runs(self):
        runs = [{"coste_usd": 0.001, "cached": False, "tokens": {"entrada": 10, "salida": 5}},
                {"coste_usd": 0.002, "cached": True, "tokens": {"entrada": 10, "salida": 5}}]
        finals = [_final("Q1", True, _answer("a"))]
        verdicts = [{"id": "Q1", "jury": True, "judges": [{"coste_usd": 0.0005, "tokens": {"entrada": 3}}]}]
        cost = metrics.cost_block(1, runs, finals, verdicts)
        self.assertEqual(cost["notifiedUsd"], 0.0015)
        self.assertEqual(cost["searchers"]["tokensPerAnswer"], 30.0)
        self.assertEqual(cost["judges"]["tokensPerAnswer"], 3.0)


class UnknownCostTest(unittest.TestCase):
    def test_unknown_cost_is_counted_not_zero(self):
        runs = [{"coste_usd": None, "cached": False, "tokens": None}, {"coste_usd": 0.001, "cached": False, "tokens": None}]
        cost = metrics.cost_block(1, runs, [_final("Q1", True, _answer("a"))], [])
        self.assertEqual(cost["unknownCostRecords"], 1)
        self.assertEqual(cost["notifiedUsd"], 0.001)


class LatencyTest(unittest.TestCase):
    def test_sums_slowest_searcher_composer_and_judge(self):
        runs = [{"id": "Q1", "duracion_s": 2.0}, {"id": "Q1", "duracion_s": 5.0}]
        finals = [{**_final("Q1", True), "duracion_s": 1.0}]
        verdicts = [_verdict("Q1", True, duracion=3.0)]
        self.assertEqual(metrics.latency_block(runs, finals, verdicts), {"p50": 9.0, "p95": 9.0})


if __name__ == "__main__":
    unittest.main()
