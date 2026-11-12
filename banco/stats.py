"""Estadística pura del banco: intervalos, acuerdo entre jueces y PPI. Sin IA ni red."""

import math
from statistics import mean, pvariance


def _check_pair(a: list, b: list) -> None:
    if len(a) != len(b):
        raise ValueError("las listas deben tener la misma longitud")
    if not a:
        raise ValueError("las listas no pueden estar vacías")


def _clamp(x: float) -> float:
    return min(1.0, max(0.0, x))


def wilson(successes: int, n: int, z: float = 1.96) -> tuple[float, float]:
    if n <= 0:
        raise ValueError("n debe ser mayor que 0 para el intervalo de Wilson")
    if not 0 <= successes <= n:
        raise ValueError("los aciertos deben estar entre 0 y n")
    p = successes / n
    z2 = z * z
    denom = 1 + z2 / n
    center = (p + z2 / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z2 / (4 * n * n)) / denom
    return _clamp(center - half), _clamp(center + half)


def cohen_kappa(a: list[bool], b: list[bool]) -> float:
    _check_pair(a, b)
    n = len(a)
    observed = sum(x == y for x, y in zip(a, b)) / n
    pa = sum(a) / n
    pb = sum(b) / n
    expected = pa * pb + (1 - pa) * (1 - pb)
    if expected == 1:
        return 1.0 if observed == 1 else 0.0
    return (observed - expected) / (1 - expected)


def false_accept_rate(judge: list[bool], script: list[bool]) -> float:
    _check_pair(judge, script)
    wrong = [j for j, s in zip(judge, script) if not s]
    if not wrong:
        raise ValueError("el script no marca ninguna respuesta como mala: tasa indefinida")
    return sum(wrong) / len(wrong)


def false_reject_rate(judge: list[bool], script: list[bool]) -> float:
    _check_pair(judge, script)
    right = [j for j, s in zip(judge, script) if s]
    if not right:
        raise ValueError("el script no marca ninguna respuesta como buena: tasa indefinida")
    return sum(not j for j in right) / len(right)


def ppi_mean(judge_unlabeled: list[bool], judge_labeled: list[bool], truth_labeled: list[bool],
             z: float = 1.96) -> tuple[float, float, float]:
    if not judge_unlabeled or not judge_labeled:
        raise ValueError("hacen falta preguntas sin etiquetar y etiquetadas")
    _check_pair(judge_labeled, truth_labeled)
    unlabeled = [float(x) for x in judge_unlabeled]
    rectifier = [float(t) - float(j) for t, j in zip(truth_labeled, judge_labeled)]
    estimate = mean(unlabeled) + mean(rectifier)
    variance = pvariance(unlabeled) / len(unlabeled) + pvariance(rectifier) / len(rectifier)
    half = z * math.sqrt(variance)
    return _clamp(estimate), _clamp(estimate - half), _clamp(estimate + half)
