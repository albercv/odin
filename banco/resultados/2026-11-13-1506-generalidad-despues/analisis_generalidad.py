"""Análisis D-084 por variante y réplica: sector afirmado, fugas y acierto. Sin IA ni red.
Se ejecuta en la rama medicion/d084-generalidad (necesita bateria-conector.json y el puntuador del CTO 2).
Uso: analisis_generalidad.py <carpeta> [<carpeta>...]"""
import json, sys, statistics
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO))
from banco import capas, scoring, stats, layout as layout_mod
from banco.medir_path import puntuar

lay = layout_mod.default_layout()
data_dir = capas.data_dir(lay, ("ficticio", "generalidad"))
items = {i["id"]: i for i in capas.load_items(lay, ("conector", "generalidad"))}
TRAPS, ISOL = {"GEN24", "GEN25", "GEN26"}, {"GEN29", "GEN30", "GEN31", "GEN32"}


def analyse(folder):
    ids = json.loads((folder / "ids.json").read_text())
    ctx = scoring.build_context(data_dir, {"doc_ruta": ids["documentos"], "ficha_f": ids["fichas"]})
    runs = [json.loads(l) for l in (folder / "buscadores.jsonl").read_text().splitlines() if l.strip()]
    by_rep, examples = defaultdict(list), []
    for run in runs:
        item = items[run["id"]]
        row = puntuar.score_item(item, run.get("respuesta"), ctx) if not run.get("error") else {"correcta": False, "error": True}
        row |= {"_id": run["id"], "_cost": run.get("coste_usd") or 0, "_turns": run.get("turnos") or 0}
        by_rep[run["replica"]].append(row)
        if row.get("afirma_tipo") or row.get("fuga_aislamiento"):
            examples.append({"id": run["id"], "rep": run["replica"], "afirma": row.get("afirma_tipo"), "fuga": row.get("fuga_aislamiento"),
                             "texto": ((run.get("respuesta") or {}).get("respuesta") or "")[:300]})
    out = {}
    for rep, rows in sorted(by_rep.items()):
        ok = sum(bool(r["correcta"]) for r in rows)
        gen = [r for r in rows if items[r["_id"]]["tipo"] == "generica"]
        out[rep] = {"correct": ok, "total": len(rows), "wilson": [round(x, 3) for x in stats.wilson(ok, len(rows))],
                    "generica": f"{sum(bool(r['correcta']) for r in gen)}/{len(gen)}",
                    "sectorAsserted": sum(bool(r.get("afirma_tipo")) for r in rows),
                    "leaks": sum(bool(r.get("fuga_aislamiento")) for r in rows),
                    "traps": f"{sum(bool(r['correcta']) for r in rows if r['_id'] in TRAPS)}/{sum(r['_id'] in TRAPS for r in rows)}",
                    "isolation": f"{sum(bool(r['correcta']) for r in rows if r['_id'] in ISOL)}/{sum(r['_id'] in ISOL for r in rows)}",
                    "errors": sum(bool(r.get("error")) for r in rows),
                    "failed": sorted(r["_id"] for r in rows if not r["correcta"]),
                    "usdPerAnswer": round(statistics.mean(r["_cost"] for r in rows), 5),
                    "turnsMedian": statistics.median(r["_turns"] for r in rows)}
    return {"replicas": out, "examples": examples}


if __name__ == "__main__":
    result = {Path(f).name: analyse(Path(f)) for f in sys.argv[1:]}
    print(json.dumps(result, ensure_ascii=False, indent=1))
