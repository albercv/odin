import unittest

from banco.combine import majority_vote, single
import random

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

    def test_draws_do_not_depend_on_arrival_order(self):
        runs_by_question = {"q1": _pool({"main": 2, "topic": 6})}
        shuffled = {"q1": list(runs_by_question["q1"])}
        random.Random(3).shuffle(shuffled["q1"])
        mix = {"main": 1, "topic": 2}
        ids = lambda draws: [[r["id"] for r in d["q1"]] for d in draws]
        self.assertEqual(ids(draw_subsets(runs_by_question, mix, 5, 11)), ids(draw_subsets(shuffled, mix, 5, 11)))


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
                "invalidDraws", "measuredPairs", "valid",
            },
        )
        self.assertEqual(row["mix"], {"main": 1})
        self.assertEqual(row["n"], 1)
        self.assertEqual(row["accuracy_mean"], 1.0)
        self.assertEqual(row["accuracy_min"], 1.0)
        self.assertEqual(row["accuracy_max"], 1.0)
        self.assertEqual(set(row["tokens_by_class_mean"]), {"entrada", "salida", "cache_lectura", "cache_creacion"})

    def test_answer_none_without_error_still_counts_as_failure_without_scoring(self):
        items = [{"id": "q1"}]
        runs_by_question = {"q1": [_run("main", 0, None)]}
        calls = []

        def score(item, answer):
            calls.append(answer)
            return True

        rows = curve(runs_by_question, items, score, majority_vote, [{"main": 1}], draws=2, seed=0)
        self.assertEqual(rows[0]["accuracy_mean"], 0.0)
        self.assertEqual(rows[0]["valid"], True)
        self.assertEqual(calls, [])

    def test_run_with_error_is_a_discarded_pair_not_a_failure(self):
        items = [{"id": "q1"}, {"id": "q2"}]
        runs_by_question = {"q1": [_run("main", 0, _answer("300 €"))], "q2": [_run("main", 0, None, error="timeout")]}
        row = curve(runs_by_question, items, lambda i, a: True, majority_vote, [{"main": 1}], draws=3, seed=0)[0]
        self.assertEqual(row["accuracy_mean"], 1.0)  # q2 no entra: no baja el acierto
        self.assertEqual(row["invalidDraws"], 3)
        self.assertEqual(row["measuredPairs"], 3)
        self.assertFalse(row["valid"])  # 3 de 6 pares descartados: más del 5 %

    def test_mix_with_an_errored_member_discards_that_pair(self):
        items = [{"id": "q1"}]
        runs_by_question = {"q1": [_run("main", 0, _answer("a")), _run("topic", 0, None, error="x")]}
        row = curve(runs_by_question, items, lambda i, a: True, majority_vote, [{"main": 1, "topic": 1}], 2, 0)[0]
        self.assertEqual((row["invalidDraws"], row["measuredPairs"]), (2, 0))
        self.assertIsNone(row["accuracy_mean"])

    def test_five_percent_of_pairs_is_the_validity_limit(self):
        items = [{"id": f"q{n}"} for n in range(40)]

        def runs_with_errors(errors):
            return {item["id"]: [_run("main", 0, None, error="x") if n < errors else _run("main", 0, _answer("a"))]
                    for n, item in enumerate(items)}

        at_limit = curve(runs_with_errors(2), items, lambda i, a: True, majority_vote, [{"main": 1}], 1, 0)[0]
        over = curve(runs_with_errors(3), items, lambda i, a: True, majority_vote, [{"main": 1}], 1, 0)[0]
        self.assertEqual((at_limit["invalidDraws"], at_limit["valid"]), (2, True))
        self.assertEqual((over["invalidDraws"], over["valid"]), (3, False))

    def test_extra_s_shifts_latency_percentiles(self):
        items = [{"id": "q1"}, {"id": "q2"}]
        runs_by_question = {
            "q1": [_run("main", 0, _answer("a"), duracion_s=2.0), _run("topic", 0, None, error="x", duracion_s=None)],
            "q2": [_run("main", 0, _answer("b"), duracion_s=4.0)],
        }
        base, shifted = (
            curve(runs_by_question, items, lambda i, a: True, single, [{"main": 1}], 2, 0, extra_s=extra)[0]
            for extra in (0.0, 1.0)
        )
        self.assertEqual(shifted["latency_p50"], base["latency_p50"] + 1.0)
        self.assertEqual(shifted["latency_p95"], base["latency_p95"] + 1.0)


class ExactAggregatesTest(unittest.TestCase):
    def test_aggregates_are_exact_when_pools_equal_counts(self):
        # Pool == count: sin efecto del muestreo. None cuenta como 0.
        items = [{"id": "q1"}, {"id": "q2"}]
        runs_by_question = {
            "q1": [
                _run("main", 0, _answer("300 €"), duracion_s=2.0,
                     tokens={"entrada": 10, "salida": 2, "cache_lectura": 3, "cache_creacion": 4}, coste=0.5),
                _run("topic", 0, _answer("300 €"), duracion_s=None,
                     tokens={"entrada": None, "salida": 5, "cache_lectura": None, "cache_creacion": None}, coste=None),
            ],
            "q2": [
                _run("main", 0, _answer("500 €"), duracion_s=4.0,
                     tokens={"entrada": 1, "salida": 1, "cache_lectura": 0, "cache_creacion": 0}, coste=0.25),
                _run("topic", 0, _answer("500 €"), duracion_s=1.0, tokens=None, coste=None),
            ],
        }
        row = curve(runs_by_question, items, lambda i, a: True, single, [{"main": 1, "topic": 1}], 2, 0)[0]
        # subconjuntos: q1 -> tokens 19+5=24, coste 0.5, lat max(2,0)=2; q2 -> tokens 2, coste 0.25, lat max(4,1)=4
        self.assertEqual(row["tokens_mean"], 13.0)
        self.assertEqual(row["cost_mean"], 0.375)
        self.assertEqual(row["latency_p50"], 3.0)
        self.assertEqual(row["latency_p95"], 4.0)
        self.assertEqual(
            row["tokens_by_class_mean"],
            {"entrada": 5.5, "salida": 4.0, "cache_lectura": 1.5, "cache_creacion": 2.0},
        )


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
            {"tokens_mean": 100.0, "accuracy_mean": 0.6, "valid": True},
            {"tokens_mean": 200.0, "accuracy_mean": 0.8, "valid": True},
        ]

    def test_beats_frontier_needs_margin_over_variation(self):
        # frontera interpolada en 150 tokens: 0.7
        row = {"tokens_mean": 150.0, "accuracy_mean": 0.75, "valid": True}
        self.assertFalse(beats_single_frontier(row, self.single_rows, variation=0.1))
        row["accuracy_mean"] = 0.85
        self.assertTrue(beats_single_frontier(row, self.single_rows, variation=0.1))

    def test_frontier_uses_nearest_endpoint_outside_range(self):
        row = {"tokens_mean": 500.0, "accuracy_mean": 0.85, "valid": True}
        self.assertTrue(beats_single_frontier(row, self.single_rows, variation=0.0))
        row["accuracy_mean"] = 0.8
        self.assertFalse(beats_single_frontier(row, self.single_rows, variation=0.0))

    def test_empty_single_rows_fails(self):
        with self.assertRaises(ValueError):
            beats_single_frontier({"tokens_mean": 1.0, "accuracy_mean": 1.0, "valid": True}, [], variation=0.0)

    def test_invalid_multi_row_has_no_verdict(self):
        row = {"tokens_mean": 150.0, "accuracy_mean": 0.99, "valid": False}
        self.assertIsNone(beats_single_frontier(row, self.single_rows, variation=0.0))

    def test_invalid_single_rows_do_not_define_the_frontier(self):
        # con la fila no válida (0.99 a 150 tokens) la frontera sería 0.99 y la mezcla no la superaría
        singles = [*self.single_rows, {"tokens_mean": 150.0, "accuracy_mean": 0.99, "valid": False}]
        row = {"tokens_mean": 150.0, "accuracy_mean": 0.75, "valid": True}
        self.assertTrue(beats_single_frontier(row, singles, variation=0.0))

    def test_no_valid_single_rows_gives_no_verdict(self):
        singles = [{"tokens_mean": 100.0, "accuracy_mean": 0.6, "valid": False}]
        self.assertIsNone(beats_single_frontier({"tokens_mean": 1.0, "accuracy_mean": 1.0, "valid": True}, singles, 0.0))


class MeasuredOnlyCostTest(unittest.TestCase):
    """Tokens, coste y latencia salen solo de los pares medidos: un par con error no sesga el frontera."""

    def test_tokens_cost_and_latency_use_only_measured_pairs(self):
        items = [{"id": "q1"}, {"id": "q2"}]
        runs_by_question = {
            "q1": [_run("main", 0, _answer("a"), duracion_s=2.0, coste=0.1,
                        tokens={"entrada": 10, "salida": 0, "cache_lectura": 0, "cache_creacion": 0})],
            "q2": [_run("main", 0, None, error="x", duracion_s=100.0, coste=9.0,
                        tokens={"entrada": 1000, "salida": 0, "cache_lectura": 0, "cache_creacion": 0})],
        }
        row = curve(runs_by_question, items, lambda i, a: True, majority_vote, [{"main": 1}], draws=2, seed=0)[0]
        self.assertEqual(row["tokens_mean"], 10.0)
        self.assertAlmostEqual(row["cost_mean"], 0.1)
        self.assertEqual(row["latency_p95"], 2.0)
        self.assertEqual(row["tokens_by_class_mean"]["entrada"], 10.0)

    def test_all_pairs_discarded_has_no_cost_or_latency(self):
        items = [{"id": "q1"}]
        runs_by_question = {"q1": [_run("main", 0, None, error="x", duracion_s=5.0, coste=1.0)]}
        row = curve(runs_by_question, items, lambda i, a: True, majority_vote, [{"main": 1}], draws=2, seed=0)[0]
        self.assertFalse(row["valid"])
        self.assertIsNone(row["tokens_mean"])
        self.assertIsNone(row["latency_p50"])


class NoiseBandTest(unittest.TestCase):
    """Sin una fila por defecto válida con acierto no hay banda de ruido: no hay veredicto de frontera, y no se rompe."""

    def test_no_noise_band_without_a_valid_default_row(self):
        from banco.run import _noise_band
        self.assertIsNone(_noise_band({"valid": False, "accuracy_min": 0.5, "accuracy_max": 0.9}))
        self.assertIsNone(_noise_band({"valid": True, "accuracy_min": None, "accuracy_max": None}))
        self.assertAlmostEqual(_noise_band({"valid": True, "accuracy_min": 0.5, "accuracy_max": 0.7}), 0.1)

    def test_frontier_has_no_verdict_without_variation_or_accuracy(self):
        singles = [{"tokens_mean": 100.0, "accuracy_mean": 0.6, "valid": True}]
        row = {"tokens_mean": 150.0, "accuracy_mean": 0.9, "valid": True}
        self.assertIsNone(beats_single_frontier(row, singles, variation=None))
        self.assertIsNone(beats_single_frontier({**row, "accuracy_mean": None}, singles, variation=0.0))
        self.assertIsNone(beats_single_frontier(row, [{"tokens_mean": 100.0, "accuracy_mean": None, "valid": True}], 0.0))


if __name__ == "__main__":
    unittest.main()
