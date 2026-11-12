"""Métricas del informe: acierto, fallos graves, enfoques, acuerdo entre buscadores, jueces, latencia y coste. Sin IA ni red."""

import statistics
from collections import Counter

from banco import judge, scoring
from banco.combine import answer_signature
from banco.medir_path import puntuar
from banco.stats import cohen_kappa, false_accept_rate, false_reject_rate, ppi_mean, wilson
from banco.subsample import TOKEN_CLASSES


def _round(value, digits=4):
    return None if value is None else round(value, digits)


def _keyed(item):
    """La pregunta tiene clave de respuesta o abstención: el script puede juzgarla."""
    return bool(item.get("claves")) or bool(item.get("abstener"))


def _total_tokens(tokens):
    return sum((tokens or {}).get(cls) or 0 for cls in TOKEN_CLASSES)


def _percentiles(values):
    if not values:
        return None, None
    if len(values) == 1:
        return round(values[0], 1), round(values[0], 1)
    cuts = statistics.quantiles(values, n=100, method="inclusive")
    return round(cuts[49], 1), round(cuts[94], 1)


def accuracy_block(finals):
    correct = sum(bool(f["correcta_script"]) for f in finals)
    total = len(finals)
    low, high = wilson(correct, total)
    return {"correct": correct, "total": total, "rate": _round(correct / total), "wilson": [_round(low), _round(high)]}


def by_type_block(items, finals):
    out = {}
    for final in finals:
        row = out.setdefault(items[final["id"]]["tipo"], {"correct": 0, "total": 0})
        row["correct"] += bool(final["correcta_script"])
        row["total"] += 1
    return out


def grave_block(items, finals, ctx):
    rows = [puntuar.score_item(items[f["id"]], f["answer"], ctx) for f in finals if f["answer"] is not None]
    scored = puntuar.summarise(rows)
    return {"leaks": len(scored["fugas"]),
            "inventedCitations": sum("inventada" in r["estados_cita"] for r in rows),
            "falseNoConsta": len(scored["falsas_abstenciones"]),
            "supersededAsCurrent": len(scored["sustituidos_como_vigentes"])}


def _recall(runs, expected):
    """Recuperación del enfoque: entre las preguntas que midió con páginas esperadas, cuántas tienen alguna vista o citada."""
    by_question = {}
    for run in runs:
        by_question.setdefault(run["id"], []).append(run)
    measured = [q for q in expected if q in by_question]
    hits = [q for q in measured if any(expected[q] & scoring.observed_pages(r) for r in by_question[q])]
    return _round(len(hits) / len(measured)) if measured else None


def approach_block(items, runs, ctx, id_by_ruta):
    """Por enfoque. Recuperación sobre sus preguntas medidas con páginas esperadas. Acierto sobre sus ejecuciones
    sin error, igual que el acierto global; las ejecuciones con error se cuentan aparte en `errors`."""
    expected = {}
    for item in items.values():
        pages = scoring.expected_pages(item, id_by_ruta)
        if pages:
            expected[item["id"]] = pages
    out = {}
    for approach in sorted({r["approach"] for r in runs}):
        own = [r for r in runs if r["approach"] == approach]
        ok = [r for r in own if r.get("error") is None]
        correct = sum(scoring.is_correct(items[r["id"]], r.get("respuesta"), ctx) for r in ok)
        out[approach] = {"recall": _recall(ok, expected), "accuracy": _round(correct / len(ok)) if ok else None,
                         "errors": len(own) - len(ok)}
    return out


def agreement_block(finals, runs):
    """k = cuántos buscadores coinciden con la firma mayoritaria; el acierto es el de la respuesta final."""
    by_id = {}
    for run in runs:
        by_id.setdefault(run["id"], []).append(run)
    groups = {}
    for final in finals:
        signatures = [answer_signature(r["respuesta"]) for r in by_id.get(final["id"], [])
                      if r.get("error") is None and r.get("respuesta") is not None]
        k = max(Counter(signatures).values(), default=0)
        groups.setdefault(str(k), []).append(bool(final["correcta_script"]))
    ordered = sorted(groups.items(), key=lambda kv: -int(kv[0]))
    return {k: {"questions": len(v), "accuracy": _round(sum(v) / len(v))} for k, v in ordered}


def judges_block(items, finals, verdicts):
    """Kappa y tasas frente al script solo con preguntas con clave que citan; las preguntas sin clave entran en el PPI.
    Los «no consta» no los juzga el jurado: se cuentan aparte y las de sin clave van a revisión manual."""
    jury = {v["id"]: v["jury"] for v in verdicts}
    not_judged = [f for f in finals if judge.is_no_consta(f["answer"])]
    judged = [f for f in finals if f not in not_judged and jury.get(f["id"]) is not None]
    keyed = [f for f in judged if _keyed(items[f["id"]])]
    judge_keyed = [jury[f["id"]] for f in keyed]
    truth = [bool(f["correcta_script"]) for f in keyed]
    block = {"kappa": None, "falseAcceptRate": None, "falseRejectRate": None,
             "undecided": len(finals) - len(judged), "ppi": None,
             "notJudgedNoConsta": len(not_judged),
             "manualReview": [f["id"] for f in not_judged if not _keyed(items[f["id"]])]}
    if len(set(truth)) >= 2:
        block.update(kappa=_round(cohen_kappa(judge_keyed, truth)),
                     falseAcceptRate=_round(false_accept_rate(judge_keyed, truth)),
                     falseRejectRate=_round(false_reject_rate(judge_keyed, truth)))
    judge_unkeyed = [jury[f["id"]] for f in judged if not _keyed(items[f["id"]])]
    if judge_unkeyed and judge_keyed:
        estimate, low, high = ppi_mean(judge_unkeyed, judge_keyed, truth)
        block["ppi"] = {"estimacion": _round(estimate), "inferior": _round(low), "superior": _round(high)}
    return block


def latency_block(runs, finals, verdicts):
    """Por respuesta: el buscador más lento, más el redactor, más el juez más lento."""
    slowest = {}
    for run in runs:
        slowest[run["id"]] = max(slowest.get(run["id"], 0), run.get("duracion_s") or 0)
    judge_slowest = {v["id"]: max((j.get("duracion_s") or 0 for j in v["judges"]), default=0) for v in verdicts}
    values = [slowest.get(f["id"], 0) + (f.get("duracion_s") or 0) + judge_slowest.get(f["id"], 0) for f in finals]
    p50, p95 = _percentiles(values)
    return {"p50": p50, "p95": p95}


def cost_block(runs, finals, verdicts, ledger=None):
    """Gasto notificado: solo ejecuciones nuevas, redactor y jueces. Por respuesta, los buscadores dividen entre las
    preguntas ejecutadas y el redactor, los jueces y la estrategia entre las respuestas medidas (no entre la batería)."""
    judges = [j for v in verdicts for j in v["judges"]]
    usd = lambda records: sum(r.get("coste_usd") or 0 for r in records)
    tokens = lambda records: sum(_total_tokens(r.get("tokens")) for r in records)
    executed = len({r["id"] for r in runs})
    measured = sum(f["answer"] is not None for f in finals)
    per_executed = lambda value: round(value / executed, 4) if executed else None
    per = lambda value: round(value / measured, 4) if measured else None
    fresh = [*(r for r in runs if not r.get("cached")), *finals, *judges]
    from_records = usd(fresh)
    unknown = sum(not isinstance(r.get("coste_usd"), (int, float)) for r in fresh)  # pago sin coste: no cuenta como 0
    notified = from_records if ledger is None else ledger["spent"]  # el libro es el gasto real del experimento
    return {"notifiedUsd": round(notified, 4), "notifiedUsdFromRecords": round(from_records, 4),
            "unknownCostRecords": unknown,
            "strategyUsdPerAnswer": per(usd(runs) + usd(finals)),
            "tokensPerAnswer": per(tokens(runs) + tokens(finals) + tokens(judges)),
            "searchers": {"usdPerAnswer": per_executed(usd(runs)), "tokensPerAnswer": per_executed(tokens(runs))},
            "combiner": {"usdPerAnswer": per(usd(finals)), "tokensPerAnswer": per(tokens(finals))},
            "judges": {"usdPerAnswer": per(usd(judges)), "tokensPerAnswer": per(tokens(judges))}}


def build_summary(items, ctx, runs, finals, verdicts, extra_runs=(), ledger=None):
    """Resumen completo. Las preguntas sin respuesta (parada o error) no son fallos: se cuentan aparte como no medidas."""
    by_id = {item["id"]: item for item in items}
    measured = [f for f in finals if f["answer"] is not None]
    summary = {"byApproach": approach_block(by_id, runs, ctx, scoring.ids_by_path(ctx.doc_ruta))}
    summary["cost"] = cost_block([*runs, *extra_runs], finals, verdicts, ledger)
    if finals:
        summary["notMeasured"] = len(finals) - len(measured)
    if measured:
        summary.update(accuracy=accuracy_block(measured), byType=by_type_block(by_id, measured),
                       graveFailures=grave_block(by_id, measured, ctx), agreement=agreement_block(measured, runs),
                       judges=judges_block(by_id, measured, verdicts), latency=latency_block(runs, measured, verdicts))
    return summary
