#!/usr/bin/env python3
"""Falso `claude` que cambia de modo desde la llamada FAKE_MODO_DESDE (contada en FAKE_CONTADOR), para simular un límite a mitad de lote."""
import os
import sys
from pathlib import Path

FAKE = Path(__file__).resolve().parents[2] / "experimentos" / "2026-11-12-archivo-ficticio" / "medir" / "fake_claude.py"


def contar_llamada(ruta):
    ruta = Path(ruta)
    n = int(ruta.read_text()) + 1 if ruta.exists() else 1
    ruta.write_text(str(n))
    return n


if "FAKE_MODO_DESDE" in os.environ and contar_llamada(os.environ["FAKE_CONTADOR"]) >= int(os.environ["FAKE_MODO_DESDE"]):
    os.environ["FAKE_MODO"] = os.environ["FAKE_MODO_DESDE_MODO"]
os.execv(sys.executable, [sys.executable, str(FAKE), *sys.argv[1:]])
