"""Score the answers of one variant against the resolved battery.

Usage: python puntuar.py <variant_name> <answers.json> [<answers.json> ...]
Writes resultados/<variant_name>.json and prints a summary."""
import json
import re
import sys
from collections import Counter
from difflib import SequenceMatcher

from comun import BATTERY, RESULTS, CLAVE, EXP, load_corpus, load_json, normalise, ocr_tolerant

FUZZY_THRESHOLD = 0.85


def page_of(citation):
    try:
        return int(citation.get("pagina"))
    except (TypeError, ValueError):
        return None


def fragment_status(citation, corpus):
    """'exacta' / 'aproximada' if the fragment is on the cited page, 'otra_pagina' if it is
    elsewhere in the doc, 'inventada' if it is nowhere in the cited document."""
    pages = corpus.get(citation.get("doc", ""), {})
    fragment = normalise(citation.get("fragmento", ""))
    if not pages or len(fragment) < 8:
        return "inventada"
    cited = normalise(pages.get(page_of(citation), ""))
    if fragment in cited:
        return "exacta"
    if best_ratio(fragment, cited) >= FUZZY_THRESHOLD:
        return "aproximada"
    others = [normalise(t) for p, t in pages.items() if p != page_of(citation)]
    return "otra_pagina" if any(fragment in t or best_ratio(fragment, t) >= FUZZY_THRESHOLD for t in others) else "inventada"


def best_ratio(fragment, text):
    if not text:
        return 0.0
    size, step = len(fragment), max(1, len(fragment) // 4)
    windows = (text[i:i + size] for i in range(0, max(1, len(text) - size + 1), step))
    return max(SequenceMatcher(None, fragment, w).ratio() for w in windows)


def has_key(answer, alternatives):
    for key in alternatives:
        numeric = any(ch.isdigit() for ch in key)
        if ocr_tolerant(normalise(key), numeric) in ocr_tolerant(normalise(answer), numeric):
            return True
    return False


def expected_hits(item, citations):
    cited = {(c.get("doc"), page_of(c)) for c in citations}
    hits = [any((e["doc"], p) in cited for p in e["paginas"]) for e in item["citas_resueltas"]]
    return all(hits) if item.get("todas_las_citas") else any(hits)


def score_item(item, answer, corpus):
    citations = answer.get("citas", []) if answer else []
    statuses = [fragment_status(c, corpus) for c in citations]
    abstained = bool(answer and answer.get("no_lo_se"))
    result = {"id": item["id"], "categoria": item["categoria"], "respondida": answer is not None,
              "abstiene": abstained, "estados_cita": statuses}
    if item.get("abstener"):
        result["correcta"] = abstained and "inventada" not in statuses
        return result
    text = (answer or {}).get("respuesta", "")
    found = [has_key(text, alts) for alts in item["claves"]]
    result["claves"] = all(found)
    result["exhaustividad"] = round(sum(found) / len(found), 2)
    result["cita_esperada"] = expected_hits(item, citations)
    result["menciona_antiguo"] = any(has_key(text, [p]) for p in item.get("prohibidas", []))
    result["orden_correcto"] = in_order(text, item["orden"]) if "orden" in item else True
    # a false positive is citing a document the answer must leave out; merely naming it ("2025-009 is not about water") is fine
    result["falso_positivo"] = any(c.get("doc") in item.get("docs_prohibidos", []) for c in citations)
    result["correcta"] = (result["claves"] and result["cita_esperada"] and not abstained
                          and result["orden_correcto"] and not result["falso_positivo"])
    result["detecta_contradiccion"] = any(has_key(text, [b]) for b in item.get("bonus", []))
    return result


def in_order(answer, keys):
    """True if the first mention of each number appears in the given order; 150 never matches 1.150 or 150.000."""
    text = normalise(answer)
    found = [re.search(rf"(?<!\d){re.escape(normalise(k))}(?!\d)", text) for k in keys]
    return all(found) and [m.start() for m in found] == sorted(m.start() for m in found)


def summarise(rows):
    answerable = [r for r in rows if "claves" in r]
    unanswerable = [r for r in rows if "claves" not in r]
    statuses = Counter(s for r in rows for s in r["estados_cita"])
    by_category = Counter()
    totals = Counter()
    for r in rows:
        totals[r["categoria"]] += 1
        by_category[r["categoria"]] += r["correcta"]
    return {
        "aciertos": sum(r["correcta"] for r in answerable), "respondibles": len(answerable),
        "abstencion_correcta": sum(r["correcta"] for r in unanswerable), "sin_respuesta": len(unanswerable),
        "falsas_abstenciones": sum(r["abstiene"] for r in answerable),
        "estados_cita": dict(statuses),
        "por_categoria": {c: f"{by_category[c]}/{totals[c]}" for c in sorted(totals)},
        "menciona_antiguo": [r["id"] for r in answerable if r.get("menciona_antiguo")],
        "exhaustividad_media": round(sum(r["exhaustividad"] for r in answerable) / len(answerable), 2) if answerable else None,
        "orden_incorrecto": [r["id"] for r in answerable if not r["orden_correcto"]],
        "falsos_positivos": [r["id"] for r in answerable if r["falso_positivo"]],
        "detecta_contradiccion": [r["id"] for r in answerable if r.get("detecta_contradiccion")],
        "fallos": [r["id"] for r in rows if not r["correcta"]],
    }


def main(variant, answer_files):
    corpus = load_corpus()
    battery = load_json(BATTERY)
    answers = {a["id"]: a for f in answer_files for a in load_json(f)}
    rows = [score_item(item, answers.get(item["id"]), corpus) for item in battery if item["id"] in answers]
    summary = summarise(rows)
    out = {"variante": variant, "resumen": summary, "detalle": rows}
    (RESULTS / f"{variant}.json").write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    print(variant, json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
