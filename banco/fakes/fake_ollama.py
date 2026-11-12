"""Ollama falso en 127.0.0.1 para el juez: start_fake_ollama(mode, received=None) -> (url, stop).
Modos: ok, no_json, sin_campos, sin_metricas (duración nula y sin contadores). Exige format y stream false;
cada cuerpo recibido se añade a received si se pasa una lista."""
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

OK_VERDICT = {"correcta": True, "respaldada": True, "datosSinRespaldo": [], "motivo": "La respuesta coincide con las páginas."}
CONTENTS = {"ok": json.dumps(OK_VERDICT), "no_json": "No puedo dar JSON.", "sin_campos": json.dumps({"correcta": True}),
            "sin_metricas": json.dumps(OK_VERDICT)}
METRICS = {"total_duration": 2_000_000_000, "prompt_eval_count": 11, "eval_count": 7}
NULL_METRICS = {"total_duration": None}


def start_fake_ollama(mode, received=None):
    content = CONTENTS[mode]
    metrics = NULL_METRICS if mode == "sin_metricas" else METRICS

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
            if received is not None:
                received.append(body)
            valid = bool(body.get("format")) and body.get("stream") is False
            response = json.dumps({"model": body.get("model"), "message": {"role": "assistant", "content": content},
                                   "done": True, **metrics})
            self.send_response(200 if valid else 400)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(response.encode("utf-8"))

        def log_message(self, *args):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()

    def stop():
        server.shutdown()
        server.server_close()

    return f"http://127.0.0.1:{server.server_port}", stop
