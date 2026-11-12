"""Combinadores puros de respuestas de buscadores y fusión de páginas (RRF). Sin IA ni red."""

from fractions import Fraction


NO_CONSENSUS_ANSWER = "No consta: los buscadores no coincidieron."


def _usable(runs: list[dict]) -> list[dict]:
    return [r for r in runs if r.get("error") is None and r.get("respuesta") is not None]


def answer_signature(answer: dict) -> tuple[bool, frozenset[str]]:
    """Firma de una respuesta: abstención y conjunto de documentos o fichas citados. Sin cifras ni páginas: es la
    que mejor separa en lo medido (D-067)."""
    sources = frozenset(c.get("documentId") or c.get("cardId") for c in answer["citas"])
    return (bool(answer["no_lo_se"]), sources)


def single(runs: list[dict]) -> dict:
    main = [r for r in runs if r["approach"] == "main" and r["replica"] == 0]
    if not main:
        raise ValueError("no hay ejecución 'main' réplica 0 para la respuesta única")
    run = main[0]
    if run.get("error") is not None or run.get("respuesta") is None:
        return None
    return run["respuesta"]


def _representative(group: list[dict]) -> dict:
    best = min(group, key=lambda r: (-len(r["respuesta"]["citas"]), r["replica"]))
    return best["respuesta"]


def majority_vote(runs: list[dict]) -> dict | None:
    usable = _usable(runs)
    if not usable:
        return None
    groups: dict[tuple, list[dict]] = {}
    for run in usable:
        groups.setdefault(answer_signature(run["respuesta"]), []).append(run)
    sizes = {sig: len(members) for sig, members in groups.items()}
    top_size = max(sizes.values())
    leaders = [sig for sig, size in sizes.items() if size == top_size]
    if len(leaders) > 1:
        return {"respuesta": NO_CONSENSUS_ANSWER, "no_lo_se": True, "citas": []}
    return _representative(groups[leaders[0]])


def _ranked_pages(run: dict) -> list[tuple[str, int]]:
    citations = run["respuesta"]["citas"]
    seen = [v for query in run["consultas"] for v in query["vistos"]]
    pairs = [(e.get("documentId"), e.get("pagina")) for e in citations + seen]
    located = [p for p in pairs if p[0] is not None and p[1] is not None]
    return list(dict.fromkeys(located))


def rrf_pages(runs: list[dict], k: int = 60, top: int = 5) -> list[tuple[str, int]]:
    scores: dict[tuple[str, int], Fraction] = {}
    for run in _usable(runs):
        for rank, page in enumerate(_ranked_pages(run), start=1):
            scores[page] = scores.get(page, Fraction(0)) + Fraction(1, k + rank)
    ordered = sorted(scores, key=lambda page: -scores[page])
    return ordered[:top]
