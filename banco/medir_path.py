"""Único punto que expone a `banco` el puntuador y el ejecutor del experimento de archivo ficticio."""

import sys
from pathlib import Path

_EXP = Path(__file__).resolve().parent.parent / "experimentos" / "2026-11-12-archivo-ficticio"
for _carpeta in (_EXP / "medir", _EXP / "pila"):
    if str(_carpeta) not in sys.path:
        sys.path.insert(0, str(_carpeta))

import carga as carga_mod  # noqa: E402
import ejecutar  # noqa: E402
import puntuar  # noqa: E402
import sustituciones  # noqa: E402

__all__ = ["carga_mod", "ejecutar", "puntuar", "sustituciones"]
