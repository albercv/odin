import math
import unittest

from banco.stats import (
    cohen_kappa,
    false_accept_rate,
    false_reject_rate,
    ppi_mean,
    wilson,
)


class WilsonTest(unittest.TestCase):
    def test_wilson_201_of_216(self):
        low, high = wilson(201, 216)
        self.assertAlmostEqual(low, 0.8886, places=4)
        self.assertAlmostEqual(high, 0.9575, places=4)

    def test_wilson_edges(self):
        with self.assertRaises(ValueError):
            wilson(0, 0)
        low, high = wilson(0, 10)
        self.assertEqual(low, 0)
        self.assertGreater(high, 0)

    def test_wilson_rejects_successes_outside_range(self):
        with self.assertRaises(ValueError):
            wilson(11, 10)
        with self.assertRaises(ValueError):
            wilson(-1, 10)


class CohenKappaTest(unittest.TestCase):
    def test_kappa_perfect_and_constant(self):
        self.assertEqual(cohen_kappa([True, False], [True, False]), 1.0)
        self.assertEqual(cohen_kappa([True, True], [True, True]), 1.0)
        self.assertEqual(cohen_kappa([True, True], [False, False]), 0.0)

    def test_kappa_known_value(self):
        a = [True, True, False, False]
        b = [True, False, False, False]
        self.assertAlmostEqual(cohen_kappa(a, b), 0.5)

    def test_kappa_rejects_bad_input(self):
        with self.assertRaises(ValueError):
            cohen_kappa([True], [True, False])
        with self.assertRaises(ValueError):
            cohen_kappa([], [])


class ErrorRateTest(unittest.TestCase):
    def test_false_accept_rate(self):
        judge = [True, True, False, True]
        script = [False, False, False, True]
        # Entre las 3 que el script marca como malas, el juez acepta 2.
        self.assertAlmostEqual(false_accept_rate(judge, script), 2 / 3)

    def test_false_accept_rate_without_wrong_cases_is_undefined(self):
        with self.assertRaises(ValueError):
            false_accept_rate([True, False], [True, True])

    def test_false_reject_rate(self):
        judge = [False, True, False, True]
        script = [True, True, False, True]
        # Entre las 3 que el script marca como buenas, el juez rechaza 1.
        self.assertAlmostEqual(false_reject_rate(judge, script), 1 / 3)

    def test_false_reject_rate_without_correct_cases_is_undefined(self):
        with self.assertRaises(ValueError):
            false_reject_rate([True, False], [False, False])


class PpiMeanTest(unittest.TestCase):
    def test_ppi_corrects_biased_judge(self):
        judge_unlabeled = [True] * 10
        judge_labeled = [True] * 10
        truth_labeled = [True] * 8 + [False] * 2
        estimate, low, high = ppi_mean(judge_unlabeled, judge_labeled, truth_labeled)
        # Varianza: 0 (juez constante) + pvarianza(rectificador) / 10, con 8 ceros y 2 valores -1: 0,16 / 10.
        half = 1.96 * math.sqrt(0.16 / 10)
        self.assertAlmostEqual(estimate, 0.8)
        self.assertAlmostEqual(low, 0.8 - half, places=3)
        self.assertAlmostEqual(low, 0.552, places=3)
        self.assertEqual(high, 1.0)

    def test_ppi_clips_estimate_into_unit_interval(self):
        self.assertEqual(ppi_mean([True], [False], [True]), (1.0, 1.0, 1.0))
        self.assertEqual(ppi_mean([False], [True], [False]), (0.0, 0.0, 0.0))

    def test_ppi_rejects_empty_or_mismatched_input(self):
        with self.assertRaises(ValueError):
            ppi_mean([], [True], [True])
        with self.assertRaises(ValueError):
            ppi_mean([True], [], [])
        with self.assertRaises(ValueError):
            ppi_mean([True], [True, False], [True])


if __name__ == "__main__":
    unittest.main()
