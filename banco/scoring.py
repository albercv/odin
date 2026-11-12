"""Puntuación con el puntuador de medir y textos de página para los jueces. Sin IA ni red."""

import json
from pathlib import Path

from banco.medir_path import puntuar, sustituciones

NOT_FOUND = "(no encontrado)"


def build_context(exp, stack):
    """Contexto del puntuador como puntuar.main: corpus, manifiesto y mapas de ids (de carga.json o de ids.json)."""
    data_dir = Path(exp) / "datos"
    corpus = puntuar.load_corpus(data_dir / "corpus")
    manifest = json.loads((data_dir / "manifiesto.json").read_text(encoding="utf-8"))
    key_rows = json.loads((data_dir / "claves.json").read_text(encoding="utf-8"))
    superseded = {old for old, _ in sustituciones.parejas(key_rows, manifest)}
    return puntuar.Contexto(corpus, manifest, stack["doc_ruta"], stack["ficha_f"], superseded)


def ids_by_path(doc_ruta):
    return {ruta: ident for ident, ruta in doc_ruta.items()}


def is_correct(item, answer, ctx):
    """Acierto según el script. Sin respuesta cuenta como fallo."""
    return answer is not None and bool(puntuar.score_item(item, answer, ctx)["correcta"])


def page_text(ident, pagina, ctx):
    """Texto literal que recibe el juez: la página de un documento (todo el documento si no hay página) o la ficha."""
    if ident in ctx.doc_ruta:
        pages = ctx.corpus.get(ctx.doc_ruta[ident], {})
        if pagina is None:
            return "\n\n".join(pages.values()) or NOT_FOUND
        return pages.get(pagina, NOT_FOUND)
    ficha = ctx.fichas.get(ctx.ficha_f.get(ident, ""))
    if ficha is None:
        return NOT_FOUND
    return f"{ficha.get('titulo', '')}\n{ficha.get('texto', '')}"


def expected_pages(item, id_by_ruta):
    """(documentId, página) de las citas resueltas de la pregunta; las rutas se traducen a ids de Odín."""
    return {(id_by_ruta[c["doc"]], p) for c in item.get("citas_resueltas", []) if c.get("doc") in id_by_ruta
            for p in c.get("paginas", [])}


def observed_pages(run):
    """(documentId, página) que el buscador citó o puso a la vista con sus herramientas."""
    citas = (run.get("respuesta") or {}).get("citas") or []
    vistos = [v for consulta in run.get("consultas") or [] for v in consulta.get("vistos") or []]
    return {(e.get("documentId"), e.get("pagina")) for e in citas + vistos}
