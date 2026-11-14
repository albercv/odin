"""Puntuación con el puntuador de medir y textos de página para los jueces. Sin IA ni red."""

import json
from pathlib import Path

from banco.medir_path import puntuar, sustituciones

NOT_FOUND = "(no encontrado)"


def build_context(data_dir, stack):
    """Contexto del puntuador como puntuar.main: corpus, manifiesto y mapas de ids (de carga.json o de ids.json).
    Los expedientes (si los hay) suman sus páginas al corpus y sus documentos sustituidos a los superados."""
    data_dir = Path(data_dir)
    corpus = puntuar.load_corpus(data_dir / "corpus")
    if (data_dir / "expedientes").is_dir():
        corpus |= puntuar.load_corpus(data_dir / "expedientes")
    corpus |= _texts(data_dir)
    manifest = json.loads((data_dir / "manifiesto.json").read_text(encoding="utf-8"))
    key_rows = json.loads((data_dir / "claves.json").read_text(encoding="utf-8"))
    superseded = {old for old, _ in sustituciones.parejas(key_rows, manifest)}
    superseded |= _expediente_superseded(data_dir)
    return puntuar.Contexto(corpus, manifest, stack["doc_ruta"], stack["ficha_f"], superseded)


def _texts(data_dir):
    """Páginas de textos.json (Mirador): {ruta: {página: texto}}, con las páginas como enteros."""
    path = data_dir / "textos.json"
    if not path.is_file():
        return {}
    texts = json.loads(path.read_text(encoding="utf-8"))
    return {source: {int(num): text for num, text in pages.items()} for source, pages in texts.items()}


def _expediente_superseded(data_dir):
    path = data_dir / "expedientes.json"
    if not path.is_file():
        return set()
    cases = json.loads(path.read_text(encoding="utf-8"))
    return {old for case in cases for old, _ in case.get("sustituciones", [])}


def ids_by_path(doc_paths):
    return {path: ident for ident, path in doc_paths.items()}


def is_correct(item, answer, ctx):
    """Acierto según el script. Sin respuesta cuenta como fallo."""
    return answer is not None and bool(puntuar.score_item(item, answer, ctx)["correcta"])


def page_text(ident, page, ctx):
    """Texto literal que recibe el juez: la página de un documento (todo el documento si no hay página) o la ficha."""
    if ident in ctx.doc_ruta:
        pages = ctx.corpus.get(ctx.doc_ruta[ident], {})
        if page is None:
            return "\n\n".join(pages.values()) or NOT_FOUND
        return pages.get(page, NOT_FOUND)
    card = ctx.fichas.get(ctx.ficha_f.get(ident, ""))
    if card is None:
        return NOT_FOUND
    return f"{card.get('titulo', '')}\n{card.get('texto', '')}"


def expected_pages(item, id_by_path):
    """(documentId, página) de las citas resueltas de la pregunta; las rutas se traducen a ids de Odín."""
    return {(id_by_path[c["doc"]], p) for c in item.get("citas_resueltas", []) if c.get("doc") in id_by_path
            for p in c.get("paginas", [])}


def observed_pages(run):
    """(documentId, página) que el buscador citó o puso a la vista con sus herramientas."""
    citations = (run.get("respuesta") or {}).get("citas") or []
    seen = [v for query in run.get("consultas") or [] for v in query.get("vistos") or []]
    return {(e.get("documentId"), e.get("pagina")) for e in citations + seen}
