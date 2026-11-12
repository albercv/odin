"""Submuestreo del pool de ejecuciones, curvas de acierto/coste/latencia y frontera de un solo buscador. Sin IA ni red."""

import random
import statistics
from bisect import bisect_left

TOKEN_CLASSES = ("entrada", "salida", "cache_lectura", "cache_creacion")
MAX_DISCARDED_SHARE = 0.05  # una mezcla con más pares pregunta-sorteo descartados por error no es válida


def draw_subsets(runs_by_question, mix, draws, seed):
    rng = random.Random(seed)
    result = []
    for _ in range(draws):
        draw = {}
        for question_id in sorted(runs_by_question):
            draw[question_id] = _draw_question(runs_by_question[question_id], mix, rng, question_id)
        result.append(draw)
    return result


def _draw_question(runs, mix, rng, question_id):
    picked = []
    for approach, count in mix.items():
        pool = sorted((r for r in runs if r["approach"] == approach), key=lambda r: r["replica"])
        if len(pool) < count:
            raise ValueError(f"pregunta {question_id}: hacen falta {count} ejecuciones de '{approach}' y hay {len(pool)}")
        picked.extend(rng.sample(pool, count))
    return picked


def _tokens_by_class(subset):
    totals = {cls: 0 for cls in TOKEN_CLASSES}
    for run in subset:
        for cls in TOKEN_CLASSES:
            totals[cls] += (run.get("tokens") or {}).get(cls) or 0
    return totals


def _percentiles(values):
    cuts = statistics.quantiles(values, n=100, method="inclusive")
    return cuts[49], cuts[94]


def _mean(values):
    return sum(values) / len(values)


def _accuracy_stats(per_draw_accuracies):
    known = [a for a in per_draw_accuracies if a is not None]
    return (_mean(known) if known else None, min(known, default=None), max(known, default=None))


def _has_error(subset):
    return any(run.get("error") is not None for run in subset)


def _accuracy(items, subset_by_question, score, combiner):
    """Acierto de un sorteo sobre sus pares sin error, y cuántos pares descartó por error (no son fallos)."""
    correct = measured = 0
    for item in items:
        subset = subset_by_question[item["id"]]
        if _has_error(subset):
            continue
        measured += 1
        answer = combiner(subset)
        if answer is not None and score(item, answer):
            correct += 1
    discarded = len(items) - measured
    return (correct / measured if measured else None), discarded


def curve(runs_by_question, items, score, combiner, mixes, draws, seed, extra_s=0.0):
    rows = []
    for mix in mixes:
        rows.append(_curve_row(runs_by_question, items, score, combiner, mix, draws, seed, extra_s))
    return rows


def _curve_row(runs_by_question, items, score, combiner, mix, draws, seed, extra_s):
    per_draw = draw_subsets(runs_by_question, mix, draws, seed)
    results = [_accuracy(items, subsets, score, combiner) for subsets in per_draw]
    mean, low, high = _accuracy_stats([accuracy for accuracy, _ in results])
    discarded = sum(d for _, d in results)
    pairs = len(per_draw) * len(items)
    subsets_all = [subset for subsets in per_draw for subset in subsets.values()]
    tokens = [sum(_tokens_by_class(s).values()) for s in subsets_all]
    costs = [sum((r.get("coste_usd") or 0) for r in s) for s in subsets_all]
    latencies = [latency_of(s, extra_s) for s in subsets_all]
    p50, p95 = _percentiles(latencies)
    return {
        "mix": mix,
        "n": sum(mix.values()),
        "accuracy_mean": mean,
        "accuracy_min": low,
        "accuracy_max": high,
        "invalidDraws": discarded,
        "measuredPairs": pairs - discarded,
        "valid": discarded <= MAX_DISCARDED_SHARE * pairs,
        "tokens_mean": _mean(tokens),
        "cost_mean": _mean(costs),
        "latency_p50": p50,
        "latency_p95": p95,
        "tokens_by_class_mean": {
            cls: _mean([_tokens_by_class(s)[cls] for s in subsets_all]) for cls in TOKEN_CLASSES
        },
    }


def latency_of(subset, extra_s=0.0):
    if not subset:
        raise ValueError("un subconjunto vacío no tiene latencia")
    return max((run.get("duracion_s") or 0) for run in subset) + extra_s


def _frontier_at(points, x):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    if x <= xs[0]:
        return ys[0]
    if x >= xs[-1]:
        return ys[-1]
    i = bisect_left(xs, x)
    if xs[i] == x:
        return ys[i]
    x0, x1, y0, y1 = xs[i - 1], xs[i], ys[i - 1], ys[i]
    return y0 + (y1 - y0) * (x - x0) / (x1 - x0)


def beats_single_frontier(row, single_rows, variation):
    """Solo filas válidas: una mezcla no válida no tiene veredicto y las filas no válidas no definen la frontera."""
    if not single_rows:
        raise ValueError("hacen falta filas de un solo buscador para la frontera")
    valid_singles = [r for r in single_rows if r["valid"]]
    if not row["valid"] or not valid_singles:
        return None
    points = sorted((r["tokens_mean"], r["accuracy_mean"]) for r in valid_singles)
    return row["accuracy_mean"] > _frontier_at(points, row["tokens_mean"]) + variation
