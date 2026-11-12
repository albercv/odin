#!/usr/bin/env python3
"""Falso `claude` del juez, sin MCP. FAKE_VEREDICTO: si (por defecto), no, invalido (sin campos) o tools_extra (si, con Bash en el inicio). FAKE_SLEEP: espera antes de responder."""
import json
import os
import sys
import time

sys.stdin.read()
time.sleep(float(os.environ.get("FAKE_SLEEP", "0")))
modo = os.environ.get("FAKE_VEREDICTO", "si")
VEREDICTOS = {
    "si": {"correcta": True, "respaldada": True, "datosSinRespaldo": [], "motivo": "La respuesta coincide con las páginas."},
    "no": {"correcta": False, "respaldada": False, "datosSinRespaldo": ["12 €"], "motivo": "La cifra no aparece en las páginas."},
    "invalido": {"correcta": "sí"},
}
herramientas = ["StructuredOutput"] + (["Bash"] if modo == "tools_extra" else [])
init = {"type": "system", "subtype": "init", "model": "claude-haiku-fake", "tools": herramientas, "mcp_servers": [],
        "plugins": [{"name": "cc-plugin-x", "source": "cc-plugin-x@builtin"}]}
resultado = {"type": "result", "subtype": "success", "is_error": False, "duration_api_ms": 100, "num_turns": 1,
             "total_cost_usd": 0.0005, "usage": {"input_tokens": 120, "output_tokens": 30, "cache_read_input_tokens": 0,
                                                  "cache_creation_input_tokens": 0},
             "structured_output": VEREDICTOS.get(modo, VEREDICTOS["si"])}
for evento in (init, resultado):
    print(json.dumps(evento), flush=True)
