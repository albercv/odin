import unittest

from banco.combine import majority_vote
from banco.subsample import beats_single_frontier, curve, draw_subsets, latency_of


def _answer(texto, no_lo_se=False):
    return {"respuesta": texto, "no_lo_se": no_lo_se, "citas": []}


def _run(approach="main", replica=0, respuesta=None, error=None, duracion_s=1.0, tokens=None, coste=0.0):
    return {
        "id": f"{approach}-{replica}",
        "approach": approach,
        "replica": replica,
        "cached": False,
        "respuesta": respuesta,
        "error": error,
        "consultas": [],
        "tokens": tokens or {"entrada": 0, "salida": 0, "cache_lectura": 0, "cache_creacion": 0},
        "coste_usd": coste,
        "duracion_s": duracion_s,
    }


def _pool(approaches):
    """Pool de un solo grupo: {approach: n_runs} con respuestas iguales."""
    return [
        _run(approach, replica, _answer("300 €"))
        for approach, n_runs in approaches.items()
        for replica in range(n_runs)
    ]


class DrawSubsetsTest(unittest.TestCase):
    def setUp(self):
        self.runs_by_question = {
            "q2": _pool({"main": 1, "topic": 10}),
            "q1": _pool({"main": 1, "topic": 10}),
        }
        self.mix = {"main": 1, "topic": 2}

    def test_draws_respect_mix_and_seed(self):
        draws = draw_subsets(self.runs_by_question, self.mix, draws=4, seed=7)
        self.assertEqual(len(draws), 4)
        for draw in draws:
            self.assertEqual(set(draw), {"q1", "q2"})
            for runs in draw.values():
                self.assertEqual(sorted(r["approach"] for r in runs), ["main", "topic", "topic"])
                self.assertEqual(len({r["id"] for r in runs}), len(runs))
        self.assertEqual(draws, draw_subsets(self.runs_by_question, self.mix, draws=4, seed=7))

    def test_draw_fails_when_pool_too_small(self):
        runs_by_question = {"q1": _pool({"topic": 5})}
        with self.assertRaises(ValueError):
            draw_subsets(runs_by_question, {"topic": 6}, draws=1, seed=0)


class CurveTest(unittest.TestCase):
    def test_curve_rows_have_all_fields(self):
        items = [{"id": "q1"}, {"id": "q2"}]
        runs_by_question = {
            "q1": [
                _run("main", 0, _answer("300 €"), duracion_s=2.0, tokens={"entrada": 10, "salida": 2, "cache_lectura": 3, "cache_creacion": 4}, coste=0.5),
                _run("main", 1, _answer("300 €"), duracion_s=3.0),
            ],
            "q2": [
                _run("main", 0, _answer("500 €"), duracion_s=1.0, tokens={"entrada": 1, "salida": 1, "cache_lectura": 0, "cache_creacion": 0}, coste=0.25),
                _run("main", 1, _answer("500 €"), duracion_s=4.0),
            ],
        }

        def score(item, answer):
            return answer["respuesta"] == ("300 €" if item["id"] == "q1" else "500 €")

        rows = curve(runs_by_question, items, score, majority_vote, [{"main": 1}], draws=3, seed=1)

        self.assertEqual(len(rows), 1)
        row = rows[0]
        self.assertEqual(
            set(row),
            {
                "mix", "n", "accuracy_mean", "accuracy_min", "accuracy_max", "tokens_mean",
                "cost_mean", "latency_p50", "latency_p95", "tokens_by_class_mean",
            },
        )
        self.assertEqual(row["mix"], {"main": 1})
        self.assertEqual(row["n"], 1)
        self.assertEqual(row["accuracy_mean"], 1.0)
        self.assertEqual(row["accuracy_min"], 1.0)
        self.assertEqual(row["accuracy_max"], 1.0)
        self.assertEqual(set(row["tokens_by_class_mean"]), {"entrada", "salida", "cache_lectura", "cache_creacion"})

    def test_curve_counts_none_answers_as_failures_without_scoring(self):
        items = [{"id": "q1"}]
        runs_by_question = {"q1": [_run("main", 0, None, error="timeout")]}
        calls = []

        def score(item, answer):
            calls.append(answer)
            return True

        rows = curve(runs_by_question, items, score, majority_vote, [{"main": 1}], draws=2, seed=0)
        self.assertEqual(rows[0]["accuracy_mean"], 0.0)
        self.assertEqual(calls, [])


class LatencyTest(unittest.TestCase):
    def test_latency_is_max_plus_extra(self):
        subset = [_run(duracion_s=2.0), _run(duracion_s=5.5), _run(duracion_s=None)]
        self.assertEqual(latency_of(subset, extra_s=1.5), 7.0)
        self.assertEqual(latency_of(subset), 5.5)

    def test_latency_of_empty_subset_fails(self):
        with self.assertRaises(ValueError):
            latency_of([])


class FrontierTest(unittest.TestCase):
    def setUp(self):
        self.single_rows = [
            {"tokens_mean": 100.0, "accuracy_mean": 0.6},
            {"tokens_mean": 200.0, "accuracy_mean": 0.8},
        ]

    def test_beats_frontier_needs_margin_over_variation(self):
        # frontera interpolada en 150 tokens: 0.7
        row = {"tokens_mean": 150.0, "accuracy_mean": 0.75}
        self.assertFalse(beats_single_frontier(row, self.single_rows, variation=0.1))
        row["accuracy_mean"] = 0.85
        self.assertTrue(beats_single_frontier(row, self.single_rows, variation=0.1))

    def test_frontier_uses_nearest_endpoint_outside_range(self):
        row = {"tokens_mean": 500.0, "accuracy_mean": 0.85}
        self.assertTrue(beats_single_frontier(row, self.single_rows, variation=0.0))
        row["accuracy_mean"] = 0.8
        self.assertFalse(beats_single_frontier(row, self.single_rows, variation=0.0))

    def test_empty_single_rows_fails(self):
        with self.assertRaises(ValueError):
            beats_single_frontier({"tokens_mean": 1.0, "accuracy_mean": 1.0}, [], variation=0.0)


if __name__ == "__main__":
    unittest.main()
