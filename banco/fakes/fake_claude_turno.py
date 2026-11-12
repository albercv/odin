#!/usr/bin/env python3
"""Falso `claude` que cambia de modo desde la llamada FAKE_MODO_DESDE (contada en FAKE_CONTADOR, una línea por llamada) y espera FAKE_SLEEP segundos."""
import json
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


def _sin_odin():
    """La llamada de prueba trae el MCP vacío: el fake de medir exige el de Odín, así que aquí se responde directamente."""
    cfg = json.loads(Path(sys.argv[sys.argv.index("--mcp-config") + 1]).read_text(encoding="utf-8"))
    return "odin" not in cfg.get("mcpServers", {})


if _sin_odin():
    modo = os.environ.get("FAKE_MODO", "ok")
    texto = {"limite": "Claude AI usage limit reached", "error": "fallo cualquiera"}.get(modo)
    res = {"type": "result", "subtype": "success" if texto is None else "error", "is_error": texto is not None,
           "total_cost_usd": 0.001, "result": texto or '{"ok": true}'}
    print(json.dumps(res), flush=True)
    sys.exit(0)
os.execv(sys.executable, [sys.executable, str(FAKE), *sys.argv[1:]])
