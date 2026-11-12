"""Jueces de respuestas: un veredicto por material (pregunta, respuesta y páginas citadas), con Haiku por claude -p o con Ollama local."""

import json
import os
import random
import subprocess
import tempfile
import time
import urllib.request
from collections.abc import Callable

from banco.medir_path import ejecutar

CAMPOS = {"correcta": bool, "respaldada": bool, "datosSinRespaldo": list, "motivo": str}
VERDICT_SCHEMA = {
    "type": "object", "required": list(CAMPOS), "additionalProperties": False,
    "properties": {
        "correcta": {"type": "boolean"}, "respaldada": {"type": "boolean"},
        "datosSinRespaldo": {"type": "array", "items": {"type": "string"}}, "motivo": {"type": "string"},
    },
}


def _invalido(motivo):
    return {"invalido": True, "motivo": motivo}


def _valido(datos):
    """Un veredicto con exactamente los campos del esquema y sus tipos."""
    if not isinstance(datos, dict) or set(datos) != set(CAMPOS):
        return False
    sin_respaldo = datos["datosSinRespaldo"]
    return (isinstance(datos["correcta"], bool) and isinstance(datos["respaldada"], bool) and isinstance(sin_respaldo, list)
            and all(isinstance(x, str) for x in sin_respaldo) and isinstance(datos["motivo"], str))


def _veredicto(datos):
    return datos if _valido(datos) else _invalido("veredicto con campos ausentes o de tipo incorrecto")


def _registro(model, verdict, coste, tokens, t0):
    return {"model": model, "verdict": verdict, "coste_usd": coste, "tokens": tokens,
            "duracion_s": round(time.monotonic() - t0, 1)}


def check_judge_init(init):
    """El juez solo puede tener StructuredOutput, ningún servidor MCP y plugins integrados."""
    herramientas = [t for t in init.get("tools", []) if t != "StructuredOutput"]
    plugins = [p.get("name") for p in init.get("plugins", []) if not str(p.get("source", "")).endswith("@builtin")]
    if herramientas or init.get("mcp_servers") or plugins:
        raise ejecutar.Aislamiento(f"juez no aislado: herramientas={herramientas} mcp={init.get('mcp_servers')} plugins={plugins}")


def _eventos(salida):
    eventos = []
    for linea in salida.splitlines():
        try:
            evento = json.loads(linea)
        except ValueError:
            continue
        if isinstance(evento, dict):
            eventos.append(evento)
    return eventos


def _desde_resultado(resultado):
    if resultado is None or resultado.get("is_error"):
        return _invalido("sin resultado del juez")
    return _veredicto(resultado.get("structured_output"))


def run_claude_judge(material, rubric_text, model, claude_bin, timeout):
    t0 = time.monotonic()
    orden = [claude_bin, "-p", "--model", model, "--output-format", "stream-json", "--verbose",
             "--mcp-config", '{"mcpServers":{}}', *ejecutar.OPCIONES_AISLAMIENTO, "--json-schema", json.dumps(VERDICT_SCHEMA)]
    with tempfile.TemporaryDirectory(prefix="odin-juez-") as vacia:
        try:
            p = subprocess.run(orden, input=rubric_text + "\n\n" + material, cwd=vacia, text=True, capture_output=True,
                               timeout=timeout, env={**os.environ, **ejecutar.ENTORNO_AISLADO})
        except subprocess.TimeoutExpired:
            return _registro(model, _invalido(f"tiempo agotado ({timeout} s)"), 0.0, None, t0)
    eventos = _eventos(p.stdout)
    init = next((e for e in eventos if e.get("type") == "system" and e.get("subtype") == "init"), None)
    if init is None:
        return _registro(model, _invalido(f"sin evento de inicio (código {p.returncode})"), 0.0, None, t0)
    check_judge_init(init)
    resultado = next((e for e in reversed(eventos) if e.get("type") == "result"), None)
    uso = (resultado or {}).get("usage") or {}
    tokens = {"entrada": uso.get("input_tokens"), "salida": uso.get("output_tokens"),
              "cache_lectura": uso.get("cache_read_input_tokens"), "cache_creacion": uso.get("cache_creation_input_tokens")}
    coste = (resultado or {}).get("total_cost_usd") or 0.0
    return _registro(model, _desde_resultado(resultado), coste, tokens, t0)


def run_ollama_judge(material, rubric_text, model, url, timeout):
    t0 = time.monotonic()
    cuerpo = {"model": model, "messages": [{"role": "system", "content": rubric_text}, {"role": "user", "content": material}],
              "stream": False, "format": VERDICT_SCHEMA, "options": {"temperature": 0}}
    peticion = urllib.request.Request(f"{url}/api/chat", data=json.dumps(cuerpo).encode("utf-8"), method="POST",
                                      headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(peticion, timeout=timeout) as respuesta:
            datos = json.loads(respuesta.read().decode("utf-8"))
        veredicto = _veredicto(json.loads(datos["message"]["content"]))
    except (OSError, ValueError, KeyError, TypeError) as error:
        return _registro(model, _invalido(f"respuesta de Ollama no válida: {error}"), 0.0, None, t0)
    tokens = {"entrada": datos.get("prompt_eval_count"), "salida": datos.get("eval_count")}
    return {"model": model, "verdict": veredicto, "coste_usd": 0.0, "tokens": tokens,
            "duracion_s": round(datos.get("total_duration", 0) / 1e9, 1)}


def _referencias(answer):
    """Documentos o fichas citados, sin repetir, en el orden de las citas."""
    pares = [(c.get("documentId") or c.get("cardId"), c.get("pagina")) for c in answer["citas"]]
    return list(dict.fromkeys(p for p in pares if p[0] is not None))


def judge_material(item, answer, page_text: Callable[[str, int], str], rng: random.Random) -> str:
    referencias = _referencias(answer)
    rng.shuffle(referencias)
    fuentes = []
    for n, (ident, pagina) in enumerate(referencias, start=1):
        cabecera = f"[Fuente {n}] {ident}" + ("" if pagina is None else f", página {pagina}")
        fuentes.append(f"{cabecera}\n{page_text(ident, pagina)}")
    partes = [f"Pregunta: {item['pregunta']}", f"Respuesta a evaluar:\n{answer['respuesta']}",
              "Texto literal de las páginas y fichas citadas:", *fuentes]
    return "\n\n".join(partes)


def aggregate(verdicts: list[dict], rule: str) -> bool | None:
    correctas = [v["correcta"] for v in verdicts if _valido(v)]
    if not correctas:
        return None
    si = sum(correctas)
    if rule == "majority":
        return 2 * si > len(correctas)
    if rule == "unanimous":
        return si == len(correctas)
    if rule.startswith("atLeast:"):
        return si >= int(rule.split(":", 1)[1])
    raise ValueError(f"regla de agregación desconocida: {rule}")
