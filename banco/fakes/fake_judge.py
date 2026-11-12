#!/usr/bin/env python3
"""Falso `claude` del juez, sin MCP. FAKE_VEREDICTO: si (por defecto), no, invalido (sin campos), tools_extra (Bash en el inicio),
sin_salida (sin structured_output), sin_init (sin evento de inicio), sin_tools / sin_mcp / sin_plugins (inicio sin ese campo).
FAKE_SLEEP: espera antes de responder."""
import json
import os
import sys
import time

sys.stdin.read()
time.sleep(float(os.environ.get("FAKE_SLEEP", "0")))
mode = os.environ.get("FAKE_VEREDICTO", "si")
VERDICTS = {
    "si": {"correcta": True, "respaldada": True, "datosSinRespaldo": [], "motivo": "La respuesta coincide con las páginas."},
    "no": {"correcta": False, "respaldada": False, "datosSinRespaldo": ["12 €"], "motivo": "La cifra no aparece en las páginas."},
    "invalido": {"correcta": "sí"},
}
MISSING_INIT_FIELD = {"sin_tools": "tools", "sin_mcp": "mcp_servers", "sin_plugins": "plugins"}
tools = ["StructuredOutput"] + (["Bash"] if mode == "tools_extra" else [])
init = {"type": "system", "subtype": "init", "model": "claude-haiku-fake", "tools": tools, "mcp_servers": [],
        "plugins": [{"name": "cc-plugin-x", "source": "cc-plugin-x@builtin"}]}
if mode in MISSING_INIT_FIELD:
    init.pop(MISSING_INIT_FIELD[mode])
result = {"type": "result", "subtype": "success", "is_error": False, "duration_api_ms": 100, "num_turns": 1,
          "total_cost_usd": 0.0005, "usage": {"input_tokens": 120, "output_tokens": 30, "cache_read_input_tokens": 0,
                                               "cache_creation_input_tokens": 0},
          "structured_output": VERDICTS.get(mode, VERDICTS["si"])}
if mode == "sin_salida":
    result.pop("structured_output")
events = [] if mode == "sin_init" else [init]
for event in [*events, result]:
    print(json.dumps(event), flush=True)
