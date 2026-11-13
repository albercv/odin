"""Análisis por réplica de la prueba synonyms-instruccion. Sin IA ni red."""
import json, sys, statistics
from collections import defaultdict
from pathlib import Path

REPO = Path("/Users/albercv/Documents/personalProjects/odin/.worktrees/calidad")
sys.path.insert(0, str(REPO))
from banco import scoring, stats, layout as layout_mod
from banco.medir_path import puntuar

folder = REPO / "banco/resultados/2026-11-13-0353-synonyms-instruccion"
lay = layout_mod.default_layout()
ids = json.loads((folder / "ids.json").read_text())
ctx = scoring.build_context(lay.exp, {"doc_ruta": ids["documentos"], "ficha_f": ids["fichas"]})
items = {i["id"]: i for i in json.loads(lay.battery_file.read_text()) if i["id"] in set(ids["preguntas"])}
runs = [json.loads(l) for l in (folder / "buscadores.jsonl").read_text().splitlines() if l.strip()]

cell = defaultdict(dict)          # (approach, replica) -> {qid: row}
for r in runs:
    ans = r.get("respuesta")
    if r.get("error") or ans is None:
        cell[(r["approach"], r["replica"])][r["id"]] = None
        continue
    cell[(r["approach"], r["replica"])][r["id"]] = puntuar.score_item(items[r["id"]], ans, ctx) | {"_run": r}

out = {"cells": {}, "byType": {}, "paired": {}, "cost": {}}
for (ap, rep), rows in sorted(cell.items()):
    scored = [v for v in rows.values() if v is not None]
    ok = sum(bool(v["correcta"]) for v in scored)
    n = len(rows)
    summ = puntuar.summarise([{k: v for k, v in s.items() if k != "_run"} for s in scored])
    out["cells"][f"{ap}/{rep}"] = {
        "correct": ok, "total": n, "notMeasured": n - len(scored), "rate": round(ok / n, 4),
        "wilson": [round(x, 4) for x in stats.wilson(ok, n)],
        "leaks": len(summ["fugas"]), "invented": sum("inventada" in s["estados_cita"] for s in scored),
        "falseNoConsta": len(summ["falsas_abstenciones"]), "supersededAsCurrent": len(summ["sustituidos_como_vigentes"]),
        "failedIds": sorted(q for q, v in rows.items() if v is None or not v["correcta"])}
    for q, v in rows.items():
        t = items[q]["tipo"]
        d = out["byType"].setdefault(t, {}).setdefault(f"{ap}/{rep}", [0, 0])
        d[0] += bool(v and v["correcta"]); d[1] += 1
    cs = [s["_run"]["coste_usd"] or 0 for s in scored]
    out["cost"][f"{ap}/{rep}"] = {"usdPerAnswer": round(statistics.mean(cs), 5),
                                  "turnsMedian": statistics.median(s["_run"]["turnos"] or 0 for s in scored),
                                  "durationMedianS": statistics.median(s["_run"]["duracion_s"] or 0 for s in scored)}

for rep in range(3):
    m, s = cell[("main", rep)], cell[("synonyms", rep)]
    only_s = sorted(q for q in items if (s[q] and s[q]["correcta"]) and not (m[q] and m[q]["correcta"]))
    only_m = sorted(q for q in items if (m[q] and m[q]["correcta"]) and not (s[q] and s[q]["correcta"]))
    out["paired"][rep] = {"onlySynonyms": only_s, "onlyMain": only_m}

# preguntas: cuántas réplicas acierta cada enfoque
per_q = {}
for q in items:
    per_q[q] = {ap: sum(bool(cell[(ap, r)][q] and cell[(ap, r)][q]["correcta"]) for r in range(3)) for ap in ("main", "synonyms")}
out["perQuestion"] = {q: v | {"tipo": items[q]["tipo"]} for q, v in per_q.items() if v["main"] < 3 or v["synonyms"] < 3}
json.dump(out, open(sys.argv[1], "w"), ensure_ascii=False, indent=1)
for k, v in out["cells"].items():
    print(k, {x: v[x] for x in v if x != "failedIds"})
print("cost", out["cost"])
for rep, p in out["paired"].items():
    print("rep", rep, "onlyS", len(p["onlySynonyms"]), "onlyM", len(p["onlyMain"]))
