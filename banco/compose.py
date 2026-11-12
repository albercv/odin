"""Redactor: un buscador más que compone la respuesta final desde las respuestas de los demás (composer) o desde las páginas de RRF (pageFusion)."""

import json
import tempfile
from pathlib import Path
from types import SimpleNamespace

from banco.combine import rrf_pages
from banco.medir_path import ejecutar

KINDS = ("composer", "pageFusion")
REGLA_COMPOSER = ("Escribe una única respuesta final. Cita solo fragmentos que hayas comprobado con verifyCitation. "
                  "Si las respuestas discrepan y no puedes comprobarlas, di «no consta».")
REGLA_FUSION = "Lee esas páginas con readPage y responde solo a partir de ellas."


def composer_text(runs: list[dict]) -> str:
    usable = [r for r in runs if r.get("error") is None and r.get("respuesta") is not None]
    blocks = [f"Respuesta {n}:\n{json.dumps(r['respuesta'], ensure_ascii=False)}" for n, r in enumerate(usable, start=1)]
    return "Respuestas de otros buscadores:\n\n" + "\n\n".join(blocks) + "\n\n" + REGLA_COMPOSER


def page_fusion_text(pages: list[tuple[str, int]]) -> str:
    lines = "\n".join(f"- documentId {document_id}, página {page}" for document_id, page in pages)
    return f"Páginas seleccionadas:\n{lines}\n\n{REGLA_FUSION}"


def compose(item: dict, runs: list[dict], carga: dict, settings, kind: str, model: str,
            pages: list[tuple[str, int]] | None = None) -> dict:
    if kind not in KINDS:
        raise ValueError(f"tipo de redactor desconocido: {kind}")
    if kind == "composer":
        approach_text = composer_text(runs)
    else:
        approach_text = page_fusion_text(pages if pages is not None else rrf_pages(runs))
    args = SimpleNamespace(claude_bin=settings.claude_bin, modelo=model, timeout=settings.timeout, max_turns=settings.max_turns)
    with tempfile.TemporaryDirectory(prefix="odin-redactor-") as ctemp:
        record = ejecutar.responder(item, carga, args, Path(ctemp), approach_text)
    return {**record, "approach": kind}
