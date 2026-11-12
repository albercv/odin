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
    curvas = folder / "curvas.json"
    parada = folder / "PARADA.txt"
    return {"ids": ids, "runs": _read_jsonl(folder / "buscadores.jsonl"), "turnos": _read_jsonl(folder / "turnos.jsonl"),
            "finals": _read_jsonl(folder / "final.jsonl"), "verdicts": _read_jsonl(folder / "veredictos.jsonl"),
            "curvas": json.loads(curvas.read_text(encoding="utf-8")) if curvas.exists() else None,
            "origen": _read_origin(folder), "stopped": parada.read_text(encoding="utf-8").strip() if parada.exists() else None}


def rebuild(folder, layout):
    """Recalcula el resumen desde los ficheros de la carpeta y reescribe resumen.json e informe.md."""
    folder = Path(folder)
    data = read_folder(folder)
    ctx = scoring.build_context(layout.exp, {"doc_ruta": data["ids"]["documentos"], "ficha_f": data["ids"]["fichas"]})
    wanted = set(data["ids"]["preguntas"])
    items = [i for i in json.loads(layout.bateria.read_text(encoding="utf-8")) if i["id"] in wanted]
    summary = metrics.build_summary(items, ctx, data["runs"], data["finals"], data["verdicts"], data["turnos"])
    summary.update(origen=data["origen"], stopped=data["stopped"])
    if data["curvas"] is not None:
        summary["curvas"] = data["curvas"]
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


def _tokens(value):
    return "no calculable" if value is None else f"{value:.0f}"


def _usd(value):
    return "no calculable" if value is None else f"{value:.4f} USD".replace(".", ",")


def _origin_lines(summary):
    lines = ["## Origen", ""]
    lines += [f"- {label}: {value}" for label, value in summary.get("origen", {}).items()]
    return lines + ["- Batería de desarrollo: cifras exploratorias, no una medición final.", ""]


def _accuracy_lines(summary):
    lines = ["## Acierto", ""]
    if "accuracy" not in summary:
        return lines + ["- Sin respuesta final: no se compuso ninguna respuesta.", ""]
    acc = summary["accuracy"]
    low, high = acc["wilson"]
    return lines + [f"- Acierto del script: {acc['correct']} de {acc['total']} ({_pct(acc['rate'])}); "
                    f"intervalo de Wilson al 95 %: {_pct(low)} a {_pct(high)}.", ""]


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
    lines = ["## Por enfoque", "", "| Enfoque | Recuperación | Acierto de sus ejecuciones |", "|---|---|---|"]
    rows = [f"| {name} | {_pct(row['recall'])} | {_pct(row['accuracy'])} |"
            for name, row in summary.get("byApproach", {}).items()]
    return lines + (rows or ["Sin ejecuciones de buscadores."]) + [""]


def _agreement_lines(summary):
    lines = ["## Acuerdo entre buscadores", ""]
    if "agreement" not in summary:
        return lines + ["Sin respuestas finales que cruzar.", ""]
    lines += ["| Buscadores que coinciden con la mayoría | Preguntas | Acierto de la respuesta final |", "|---|---|---|"]
    lines += [f"| {k} | {row['questions']} | {_pct(row['accuracy'])} |" for k, row in summary["agreement"].items()]
    return lines + [""]


def _judge_lines(summary):
    lines = ["## Jueces", ""]
    if "judges" not in summary:
        return lines + ["Sin respuestas que juzgar.", ""]
    jue = summary["judges"]
    ppi = jue["ppi"]
    ppi_text = ("no calculable: no hay preguntas sin clave" if ppi is None else
                f"{_num(ppi['estimacion'], 3)} (intervalo {_num(ppi['inferior'], 3)} a {_num(ppi['superior'], 3)})")
    return lines + [f"- Kappa de Cohen frente al script: {_num(jue['kappa'], 3)}",
                    f"- Aceptaciones falsas: {_pct(jue['falseAcceptRate'])}",
                    f"- Rechazos falsos: {_pct(jue['falseRejectRate'])}",
                    f"- Preguntas sin veredicto: {jue['undecided']}",
                    f"- PPI: {ppi_text}", ""]


def _cost_lines(summary):
    cost = summary["cost"]
    lines = ["## Coste y tiempo", "",
             f"- Gasto notificado: {_usd(cost['notifiedUsd'])} (ejecuciones nuevas, redactor y jueces)",
             f"- Coste de la estrategia por respuesta: {_usd(cost['strategyUsdPerAnswer'])}",
             f"- Tokens por respuesta: {_tokens(cost['tokensPerAnswer'])} (buscadores {_tokens(cost['searchers']['tokensPerAnswer'])}, "
             f"redactor {_tokens(cost['combiner']['tokensPerAnswer'])}, jueces {_tokens(cost['judges']['tokensPerAnswer'])})"]
    if "latency" in summary:
        lat = summary["latency"]
        lines.append(f"- Latencia por respuesta: p50 {_num(lat['p50'], 1)} s; p95 {_num(lat['p95'], 1)} s")
    return lines + [""]


def _mix_text(mix):
    return ", ".join(f"{approach} ×{count}" for approach, count in mix.items())


def _curve_lines(curvas):
    lines = ["## Curvas", "", f"Variación de single por defecto (media de la mitad del intervalo): "
             f"{_pct(curvas['variacion'])}.", "",
             "| Mezcla | Ejecuciones | Acierto medio [mín, máx] | Tokens medios | Coste medio | Latencia p50 / p95 | Supera single |",
             "|---|---|---|---|---|---|---|"]
    for row in curvas["mezclas"]:
        supera = "no aplica" if row["supera_single"] is None else ("sí" if row["supera_single"] else "no")
        lines.append(f"| {_mix_text(row['mix'])} | {row['n']} | {_pct(row['accuracy_mean'])} "
                     f"[{_pct(row['accuracy_min'])}, {_pct(row['accuracy_max'])}] | {_tokens(row['tokens_mean'])} | "
                     f"{_usd(row['cost_mean'])} | {_num(row['latency_p50'], 1)} / {_num(row['latency_p95'], 1)} s | {supera} |")
    lines += ["", "| Turnos máximos | Acierto | Tokens medios | Latencia p50 / p95 |", "|---|---|---|---|"]
    lines += [f"| {row['max_turns']} | {_pct(row['accuracy_mean'])} | {_tokens(row['tokens_mean'])} | "
              f"{_num(row['latency_p50'], 1)} / {_num(row['latency_p95'], 1)} s |" for row in curvas["single"]]
    return lines + [""]


def render(summary):
    origen = summary.get("origen", {})
    parts = [f"# Informe del banco: {origen.get('Configuración', '')}", ""]
    parts += _origin_lines(summary) + _accuracy_lines(summary) + _grave_lines(summary) + _approach_lines(summary)
    parts += _agreement_lines(summary) + _judge_lines(summary) + _cost_lines(summary)
    if "curvas" in summary:
        parts += _curve_lines(summary["curvas"])
    if summary.get("stopped"):
        parts += ["## Parada", "", summary["stopped"], ""]
    return "\n".join(parts)
