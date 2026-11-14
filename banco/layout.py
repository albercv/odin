"""Rutas y herramientas externas del banco. Las pruebas construyen un Layout con directorios temporales y falsos."""

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Layout:
    banco: Path
    exp: Path
    state_dir: Path
    results_dir: Path
    cache: Path
    repo: Path
    claude_bin: str
    claude_judge_bin: str
    ollama_url: str
    timeout: int


def default_layout() -> Layout:
    banco = Path(__file__).resolve().parent
    repo = banco.parent
    exp = repo / "experimentos" / "2026-11-12-archivo-ficticio"
    claude = os.environ.get("ODIN_BANCO_CLAUDE", "claude")
    return Layout(
        banco=banco, exp=exp,
        state_dir=Path(os.environ.get("PILA_ESTADO") or banco / "estado"),
        results_dir=banco / "resultados", cache=banco / "cache", repo=repo,
        claude_bin=claude, claude_judge_bin=os.environ.get("ODIN_BANCO_CLAUDE_JUEZ", claude),
        ollama_url=os.environ.get("ODIN_BANCO_OLLAMA_URL", "http://127.0.0.1:11434"), timeout=600,
    )
