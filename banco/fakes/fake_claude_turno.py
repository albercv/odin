#!/usr/bin/env python3
"""Falso `claude` que cambia de modo desde la llamada FAKE_MODO_DESDE (contada en FAKE_CONTADOR, una línea por llamada) y espera FAKE_SLEEP segundos."""
import os
import sys
import time
from pathlib import Path

FAKE = Path(__file__).resolve().parents[2] / "experimentos" / "2026-11-12-archivo-ficticio" / "medir" / "fake_claude.py"


def count_call(path):
    with open(path, "a", encoding="utf-8") as f:
        f.write("llamada\n")
    return len(Path(path).read_text().splitlines())


if "FAKE_MODO_DESDE" in os.environ and count_call(os.environ["FAKE_CONTADOR"]) >= int(os.environ["FAKE_MODO_DESDE"]):
    os.environ["FAKE_MODO"] = os.environ["FAKE_MODO_DESDE_MODO"]
time.sleep(float(os.environ.get("FAKE_SLEEP", "0")))
os.execv(sys.executable, [sys.executable, str(FAKE), *sys.argv[1:]])
