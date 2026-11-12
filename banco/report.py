"""Informe del banco: lee una carpeta de resultados y reescribe resumen.json e informe.md. Nunca lanza procesos ni IA."""

import json
from pathlib import Path

from banco import metrics, scoring


def _read_jsonl(path):
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def _read_origin(folder):
    text = (folder / "ORIGEN.txt").read_text(encoding="utf-8")
    return dict(line.split(": ", 1) for line in text.splitlines() if ": " in line)


def read_folder(folder):
    ids = json.loads((folder / "ids.json").read_text(encoding="utf-8"))
    curves_file = folder / "curvas.json"
    stop_file = folder / "PARADA.txt"
    return {"ids": ids, "runs": _read_jsonl(folder / "buscadores.jsonl"), "turn_runs": _read_jsonl(folder / "turnos.jsonl"),
            "finals": _read_jsonl(folder / "final.jsonl"), "verdicts": _read_jsonl(folder / "veredictos.jsonl"),
            "curves": json.loads(curves_file.read_text(encoding="utf-8")) if curves_file.exists() else None,
            "origin": _read_origin(folder),
            "ledger": json.loads((folder / "libro.json").read_text(encoding="utf-8")) if (folder / "libro.json").exists() else None,
            "stopped": stop_file.read_text(encoding="utf-8").strip() if stop_file.exists() else None}


def rebuild(folder, layout):
    """Recalcula el resumen desde los ficheros de la carpeta y reescribe resumen.json e informe.md."""
    folder = Path(folder)
    data = read_folder(folder)
    ctx = scoring.build_context(layout.exp, {"doc_ruta": data["ids"]["documentos"], "ficha_f": data["ids"]["fichas"]})
    wanted = set(data["ids"]["preguntas"])
    items = [i for i in json.loads(layout.battery_file.read_text(encoding="utf-8")) if i["id"] in wanted]
    summary = metrics.build_summary(items, ctx, data["runs"], data["finals"], data["verdicts"], data["turn_runs"],
                                    ledger=data["ledger"])
    summary.update(origen=data["origin"], stopped=data["stopped"])
    if data["curves"] is not None:
        summary["curvas"] = data["curves"]
    write_report(folder, summary)
    return summary


def write_report(out_dir, summary):
    out_dir = Path(out_dir)
    (out_dir / "resumen.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1), encoding="utf-8")
    (out_dir / "informe.md").write_text(render(summary), encoding="utf-8")


def _pct(value):
    return "no calculable" if value is None else f"{100 * value:.1f} %".replace(".", ",")


def _num(value, digits=2):
    return "no calculable" if value is None else f"{value:.{digits}f}".replace(".", ",")


def _usd(value):
    return "no calculable" if value is None else f"{value:.4f} USD".replace(".", ",")


def _tokens(value):
    return "no calculable" if value is None else f"{value:.0f}"


def _origin_lines(summary):
    lines = ["## Origen", ""]
    lines += [f"- {label}: {value}" for label, value in summary.get("origen", {}).items()]
    return lines + ["- Batería de desarrollo: cifras exploratorias, no una medición final.", ""]


def _accuracy_lines(summary):
    lines = ["## Acierto", ""]
    not_measured = summary.get("notMeasured")
    note = [] if not_measured is None else [f"- No medidas (parada o error): {not_measured} (no cuentan como fallo "
                                            "ni en ningún denominador)."]
    if "accuracy" not in summary:
        return lines + ["- Sin respuesta final medida: no se compuso ninguna respuesta."] + note + [""]
    acc = summary["accuracy"]
    low, high = acc["wilson"]
    return lines + [f"- Acierto del script: {acc['correct']} de {acc['total']} medidas ({_pct(acc['rate'])}); "
                    f"intervalo de Wilson al 95 %: {_pct(low)} a {_pct(high)}."] + note + [""]


def _grave_lines(summary):
    lines = ["## Fallos graves", ""]
    if "graveFailures" not in summary:
        return lines + ["- Sin respuestas finales que revisar.", ""]
    grave = summary["graveFailures"]
    return lines + [f"- Fugas de aislamiento: {grave['leaks']}",
                    f"- Respuestas con citas inventadas: {grave['inventedCitations']}",
                    f"- «No consta» falso: {grave['falseNoConsta']}",
                    f"- Sustituido presentado como vigente: {grave['supersededAsCurrent']}", ""]


def _approach_lines(summary):
    lines = ["## Por enfoque", "", "| Enfoque | Recuperación | Acierto de sus ejecuciones | Errores |", "|---|---|---|---|"]
    rows = [f"| {name} | {_pct(row['recall'])} | {_pct(row['accuracy'])} | {row.get('errors', 0)} |"
            for name, row in summary.get("byApproach", {}).items()]
    return lines + (rows or ["Sin ejecuciones de buscadores."]) + [""]


def _agreement_run_lines(block):
    inside, outside = block["inMajority"], block["outsideMajority"]
    at_60, below = block["byLevel"]["atLeast60"], block["byLevel"]["below60"]
    return [f"- Acierto de las ejecuciones que coinciden con la mayoría de su pregunta: {_pct(inside['accuracy'])} "
            f"({inside['runs']} ejecuciones)",
            f"- Acierto de las que no coinciden: {_pct(outside['accuracy'])} ({outside['runs']} ejecuciones)",
            f"- Fallos que quedan fuera de la mayoría: {_pct(block['failuresOutsideMajority'])}",
            f"- Respuesta mayoritaria por nivel de acuerdo: al menos el 60 %: {_pct(at_60['accuracy'])} "
            f"({at_60['questions']} preguntas); menos del 60 %: {_pct(below['accuracy'])} ({below['questions']} preguntas)",
            ""]


def _agreement_lines(summary):
    lines = ["## Acuerdo entre buscadores", ""]
    if "agreementRuns" in summary:
        lines += _agreement_run_lines(summary["agreementRuns"])
    if "agreement" not in summary:
        return lines + ["Sin respuestas finales que cruzar.", ""]
    lines += ["| Buscadores que coinciden con la mayoría | Preguntas | Acierto de la respuesta final |", "|---|---|---|"]
    lines += [f"| {k} | {row['questions']} | {_pct(row['accuracy'])} |" for k, row in summary["agreement"].items()]
    return lines + [""]


def _judge_lines(summary):
    lines = ["## Jueces", ""]
    if "judges" not in summary:
        return lines + ["Sin respuestas que juzgar.", ""]
    judges = summary["judges"]
    ppi = judges["ppi"]
    ppi_text = ("no calculable: no hay preguntas sin clave" if ppi is None else
                f"{_num(ppi['estimacion'], 3)} (intervalo {_num(ppi['inferior'], 3)} a {_num(ppi['superior'], 3)})")
    return lines + [f"- Kappa de Cohen frente al script: {_num(judges['kappa'], 3)}",
                    f"- Aceptaciones falsas: {_pct(judges['falseAcceptRate'])}",
                    f"- Rechazos falsos: {_pct(judges['falseRejectRate'])}",
                    f"- Preguntas sin veredicto: {judges['undecided']}",
                    f"- «No consta» sin juzgar: {judges['notJudgedNoConsta']}"
                    + (f" (revisión manual, sin clave: {', '.join(judges['manualReview'])})" if judges["manualReview"] else ""),
                    f"- PPI: {ppi_text}", ""]


def _cost_lines(summary):
    cost = summary["cost"]
    unknown = cost.get("unknownCostRecords", 0)
    lines = ["## Coste y tiempo", "",
             f"- Gasto notificado: {_usd(cost['notifiedUsd'])} (ejecuciones nuevas, redactor y jueces)",
             f"- Coste de la estrategia por respuesta: {_usd(cost['strategyUsdPerAnswer'])}",
             f"- Tokens por respuesta: {_tokens(cost['tokensPerAnswer'])} (buscadores {_tokens(cost['searchers']['tokensPerAnswer'])}, "
             f"redactor {_tokens(cost['combiner']['tokensPerAnswer'])}, jueces {_tokens(cost['judges']['tokensPerAnswer'])})",
             f"- Gasto según registros (diagnóstico): {_usd(cost['notifiedUsdFromRecords'])}",
             f"- Registros de pago con coste desconocido: {unknown}"
             + (" (el presupuesto se dio por agotado: no se lanzaron más llamadas de pago)" if unknown else "")]
    if "latency" in summary:
        lat = summary["latency"]
        lines.append(f"- Latencia estimada (jueces en paralelo) por respuesta: p50 {_num(lat['p50'], 1)} s; "
                     f"p95 {_num(lat['p95'], 1)} s")
    return lines + [""]


def _mix_text(mix):
    return ", ".join(f"{approach} ×{count}" for approach, count in mix.items())


def _beats_text(row):
    if not row["valid"]:
        return "no válida: errores"
    return "no aplica" if row["supera_single"] is None else ("sí" if row["supera_single"] else "no")


def _curve_lines(curves):
    lines = ["## Curvas", "", f"Variación de single por defecto (media de la mitad del intervalo): "
             f"{_pct(curves['variacion'])}.", "",
             "| Mezcla | Ejecuciones | Acierto medio [mín, máx] | Tokens medios | Coste medio | Latencia p50 / p95 | Supera single |",
             "|---|---|---|---|---|---|---|"]
    for row in curves["mezclas"]:
        lines.append(f"| {_mix_text(row['mix'])} | {row['n']} | {_pct(row['accuracy_mean'])} "
                     f"[{_pct(row['accuracy_min'])}, {_pct(row['accuracy_max'])}] | {_tokens(row['tokens_mean'])} | "
                     f"{_usd(row['cost_mean'])} | {_num(row['latency_p50'], 1)} / {_num(row['latency_p95'], 1)} s | "
                     f"{_beats_text(row)} |")
    lines += ["", "| Turnos máximos | Acierto | Tokens medios | Latencia p50 / p95 | Válida |", "|---|---|---|---|---|"]
    lines += [f"| {row['max_turns']} | {_pct(row['accuracy_mean'])} | {_tokens(row['tokens_mean'])} | "
              f"{_num(row['latency_p50'], 1)} / {_num(row['latency_p95'], 1)} s | {'sí' if row['valid'] else 'no válida: errores'} |"
              for row in curves["single"]]
    return lines + [""]


def render(summary):
    origin = summary.get("origen", {})
    parts = [f"# Informe del banco: {origin.get('Configuración', '')}", ""]
    parts += _origin_lines(summary) + _accuracy_lines(summary) + _grave_lines(summary) + _approach_lines(summary)
    parts += _agreement_lines(summary) + _judge_lines(summary) + _cost_lines(summary)
    if "curvas" in summary:
        parts += _curve_lines(summary["curvas"])
    if summary.get("stopped"):
        parts += ["## Parada", "", summary["stopped"], ""]
    return "\n".join(parts)
