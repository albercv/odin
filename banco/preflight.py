"""Llamada de prueba antes del primer grupo: una sola llamada mínima a claude con el modelo de los buscadores, el mismo
aislamiento y sin MCP de Odín. Su coste va al libro del experimento."""

import json
import os
import subprocess
import tempfile
from pathlib import Path

from banco.medir_path import ejecutar

PROMPT = 'Responde solo con este JSON, sin nada más: {"ok": true}'


def build_command(claude_bin, model, mcp_config):
    """Como las sesiones de búsqueda, pero con la configuración MCP vacía: ni herramientas ni servidores de Odín."""
    return [claude_bin, "-p", "--model", model, "--output-format", "stream-json", "--verbose",
            "--mcp-config", str(mcp_config), *ejecutar.OPCIONES_AISLAMIENTO]


def _result(stdout):
    for line in reversed(stdout.splitlines()):
        try:
            event = json.loads(line)
        except ValueError:
            continue
        if isinstance(event, dict) and event.get("type") == "result":
            return event
    return None


PREFLIGHT_NO_COST_NOTE = "prueba sin coste notificado"  # su coste no se conoce: se anota con 0 y no bloquea el libro


def _call(claude_bin, model, timeout):
    """Lanza la llamada. Devuelve (proceso, None) o (None, motivo) si no llegó a lanzarse o agotó el tiempo."""
    with tempfile.TemporaryDirectory(prefix="odin-prueba-") as d:
        cfg = Path(d) / "mcp.json"
        cfg.write_text('{"mcpServers": {}}', encoding="utf-8")
        try:
            return subprocess.run(build_command(claude_bin, model, cfg), input=PROMPT, cwd=d, text=True,
                                  capture_output=True, timeout=timeout,
                                  env={**os.environ, **ejecutar.ENTORNO_AISLADO}), None
        except subprocess.TimeoutExpired:
            return None, f"tiempo agotado ({timeout} s)"
        except OSError as error:
            return None, f"no se pudo lanzar claude: {error}"


def run_preflight(claude_bin, model, timeout, ledger):
    """Devuelve None si la llamada de prueba fue bien, o el motivo del fallo. Su coste va al libro; si no se conoce
    (sin resultado, sin total_cost_usd o tiempo agotado), se anota con 0 y el motivo, y el libro sigue usable."""
    proc, error = _call(claude_bin, model, timeout)
    if proc is None:
        ledger.charge(0.0, note=PREFLIGHT_NO_COST_NOTE)
        return error
    result = _result(proc.stdout)
    cost = None if result is None else result.get("total_cost_usd")
    if isinstance(cost, (int, float)) and not isinstance(cost, bool):
        ledger.charge(cost)
    else:
        ledger.charge(0.0, note=PREFLIGHT_NO_COST_NOTE)
    if result is None:
        return f"sin resultado (código {proc.returncode}): {proc.stderr[-300:]}"
    if result.get("is_error"):
        return str(result.get("result") or result.get("subtype"))[:500]
    return None
