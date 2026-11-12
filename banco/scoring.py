"""Puntuación con el puntuador de medir y textos de página para los jueces. Sin IA ni red."""

import json
from pathlib import Path

from banco.medir_path import puntuar, sustituciones

NO_ENCONTRADO = "(no encontrado)"


def build_context(exp, stack):
    """Contexto del puntuador como puntuar.main: corpus, manifiesto y mapas de ids (de carga.json o de ids.json)."""
    datos = Path(exp) / "datos"
    corpus = puntuar.load_corpus(datos / "corpus")
    man = json.loads((datos / "manifiesto.json").read_text(encoding="utf-8"))
    claves = json.loads((datos / "claves.json").read_text(encoding="utf-8"))
    reales = {viejo for viejo, _ in sustituciones.parejas(claves, man)}
    return puntuar.Contexto(corpus, man, stack["doc_ruta"], stack["ficha_f"], reales)


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
            return "\n\n".join(pages.values()) or NO_ENCONTRADO
        return pages.get(pagina, NO_ENCONTRADO)
    ficha = ctx.fichas.get(ctx.ficha_f.get(ident, ""))
    if ficha is None:
        return NO_ENCONTRADO
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
