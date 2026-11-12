"""Jueces de respuestas: un veredicto por material (pregunta, respuesta y páginas citadas), con Haiku por claude -p o con Ollama local."""

import json
import os
import random
import subprocess
import tempfile
import time
import urllib.parse
import urllib.request
from collections.abc import Callable

from banco.medir_path import ejecutar

FIELDS = {"correcta": bool, "respaldada": bool, "datosSinRespaldo": list, "motivo": str}
VERDICT_SCHEMA = {
    "type": "object", "required": list(FIELDS), "additionalProperties": False,
    "properties": {
        "correcta": {"type": "boolean"}, "respaldada": {"type": "boolean"},
        "datosSinRespaldo": {"type": "array", "items": {"type": "string"}}, "motivo": {"type": "string"},
    },
}
LOCAL_HOSTS = {"127.0.0.1", "localhost", "::1"}
OLLAMA_PREFIX = "ollama:"


def _invalid(reason):
    return {"invalido": True, "motivo": reason}


def _is_valid(data):
    """Un veredicto con exactamente los campos del esquema y sus tipos."""
    if not isinstance(data, dict) or set(data) != set(FIELDS):
        return False
    unsupported = data["datosSinRespaldo"]
    return (isinstance(data["correcta"], bool) and isinstance(data["respaldada"], bool) and isinstance(unsupported, list)
            and all(isinstance(x, str) for x in unsupported) and isinstance(data["motivo"], str))


def _verdict_or_invalid(data):
    return data if _is_valid(data) else _invalid("veredicto con campos ausentes o de tipo incorrecto")


def _record(model, verdict, cost, tokens, t0):
    return {"model": model, "verdict": verdict, "coste_usd": cost, "tokens": tokens,
            "duracion_s": round(time.monotonic() - t0, 1)}


def check_judge_init(init):
    """Exige los tres campos del inicio: solo StructuredOutput, ningún servidor MCP y plugins integrados."""
    for field in ("tools", "mcp_servers", "plugins"):
        if not isinstance(init.get(field), list):
            raise ejecutar.Aislamiento(f"juez no aislado: falta el campo {field} en el evento de inicio")
    tools = [t for t in init["tools"] if t != "StructuredOutput"]
    foreign_plugins = [p for p in init["plugins"]
                       if not (isinstance(p, dict) and str(p.get("source", "")).endswith("@builtin"))]
    if tools or init["mcp_servers"] or foreign_plugins:
        raise ejecutar.Aislamiento(f"juez no aislado: herramientas={tools} mcp={init['mcp_servers']} plugins={foreign_plugins}")


def _events(output):
    events = []
    for line in output.splitlines():
        try:
            event = json.loads(line)
        except ValueError:
            continue
        if isinstance(event, dict):
            events.append(event)
    return events


def _from_result(result):
    if result is None or result.get("is_error"):
        return _invalid("sin resultado del juez")
    return _verdict_or_invalid(result.get("structured_output"))


def run_claude_judge(material, rubric_text, model, claude_bin, timeout):
    t0 = time.monotonic()
    command = [claude_bin, "-p", "--model", model, "--output-format", "stream-json", "--verbose",
               "--mcp-config", '{"mcpServers":{}}', *ejecutar.OPCIONES_AISLAMIENTO, "--json-schema", json.dumps(VERDICT_SCHEMA)]
    with tempfile.TemporaryDirectory(prefix="odin-juez-") as empty_dir:
        try:
            proc = subprocess.run(command, input=rubric_text + "\n\n" + material, cwd=empty_dir, text=True,
                                  capture_output=True, timeout=timeout, env={**os.environ, **ejecutar.ENTORNO_AISLADO})
        except subprocess.TimeoutExpired:
            return _record(model, _invalid(f"tiempo agotado ({timeout} s)"), None, None, t0)
    events = _events(proc.stdout)
    init = next((e for e in events if e.get("type") == "system" and e.get("subtype") == "init"), None)
    if init is None:
        return _record(model, _invalid(f"sin evento de inicio (código {proc.returncode})"), None, None, t0)
    check_judge_init(init)
    result = next((e for e in reversed(events) if e.get("type") == "result"), None)
    usage = (result or {}).get("usage") or {}
    tokens = {"entrada": usage.get("input_tokens"), "salida": usage.get("output_tokens"),
              "cache_lectura": usage.get("cache_read_input_tokens"), "cache_creacion": usage.get("cache_creation_input_tokens")}
    cost = (result or {}).get("total_cost_usd")  # None si falta: coste desconocido, no 0
    return _record(model, _from_result(result), cost, tokens, t0)


def run_ollama_judge(material, rubric_text, model, url, timeout):
    host = urllib.parse.urlsplit(url).hostname
    if host not in LOCAL_HOSTS:
        raise ValueError(f"el juez de Ollama solo admite hosts locales (127.0.0.1, localhost, ::1), no {host}")
    t0 = time.monotonic()
    model = model.removeprefix(OLLAMA_PREFIX)
    body = {"model": model, "messages": [{"role": "system", "content": rubric_text}, {"role": "user", "content": material}],
            "stream": False, "format": VERDICT_SCHEMA, "options": {"temperature": 0}}
    request = urllib.request.Request(f"{url}/api/chat", data=json.dumps(body).encode("utf-8"), method="POST",
                                     headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            data = json.loads(response.read().decode("utf-8"))
        verdict = _verdict_or_invalid(json.loads(data["message"]["content"]))
    except (OSError, ValueError, KeyError, TypeError) as error:
        return _record(model, _invalid(f"respuesta de Ollama no válida: {error}"), 0.0, None, t0)
    tokens = {"entrada": data.get("prompt_eval_count"), "salida": data.get("eval_count")}
    return {"model": model, "verdict": verdict, "coste_usd": 0.0, "tokens": tokens,
            "duracion_s": _seconds(data.get("total_duration"))}


def _seconds(nanoseconds):
    """Duración de Ollama en segundos; cualquier valor que no sea numérico cuenta como 0."""
    return round(nanoseconds / 1e9, 1) if isinstance(nanoseconds, (int, float)) else 0.0


def _references(answer):
    """Documentos o fichas citados, sin repetir, en el orden de las citas."""
    pairs = [(c.get("documentId") or c.get("cardId"), c.get("pagina")) for c in answer["citas"]]
    return list(dict.fromkeys(p for p in pairs if p[0] is not None))


def judge_material(item, answer, page_text: Callable[[str, int], str], rng: random.Random) -> str:
    refs = _references(answer)
    rng.shuffle(refs)
    sources = []
    for n, (ident, page) in enumerate(refs, start=1):
        header = f"[Fuente {n}] {ident}" + ("" if page is None else f", página {page}")
        sources.append(f"{header}\n{page_text(ident, page)}")
    parts = [f"Pregunta: {item['pregunta']}", f"Respuesta a evaluar:\n{answer['respuesta']}",
             "Texto literal de las páginas y fichas citadas:", *sources]
    return "\n\n".join(parts)


def aggregate(verdicts: list[dict], rule: str) -> bool | None:
    valid = [v["correcta"] for v in verdicts if _is_valid(v)]
    if not valid:
        return None
    yes = sum(valid)
    if rule == "majority":
        return 2 * yes > len(valid)
    if rule == "unanimous":
        return yes == len(valid)
    if rule.startswith("atLeast:"):
        return yes >= int(rule.split(":", 1)[1])
    raise ValueError(f"regla de agregación desconocida: {rule}")
