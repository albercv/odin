"""Ollama falso en 127.0.0.1 para el juez: start_fake_ollama(modo) -> (url, parar). Modos: ok, no_json, sin_campos. Exige format y stream false."""
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

VEREDICTO_OK = {"correcta": True, "respaldada": True, "datosSinRespaldo": [], "motivo": "La respuesta coincide con las páginas."}
CONTENIDOS = {"ok": json.dumps(VEREDICTO_OK), "no_json": "No puedo dar JSON.", "sin_campos": json.dumps({"correcta": True})}


def start_fake_ollama(modo):
    contenido = CONTENIDOS[modo]

    class Manejador(BaseHTTPRequestHandler):
        def do_POST(self):
            cuerpo = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
            valido = cuerpo.get("format") and cuerpo.get("stream") is False
            respuesta = json.dumps({"model": cuerpo.get("model"), "message": {"role": "assistant", "content": contenido},
                                    "done": True, "total_duration": 2_000_000_000, "prompt_eval_count": 11, "eval_count": 7})
            self.send_response(200 if valido else 400)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(respuesta.encode("utf-8"))

        def log_message(self, *args):
            pass

    servidor = ThreadingHTTPServer(("127.0.0.1", 0), Manejador)
    threading.Thread(target=servidor.serve_forever, daemon=True).start()

    def parar():
        servidor.shutdown()
        servidor.server_close()

    return f"http://127.0.0.1:{servidor.server_port}", parar
