import contextlib
import hashlib
import io
import json
import re
import tempfile
import threading
import unittest
import urllib.parse
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from unittest import mock

import datos_prueba as dp
import primera_carga as pc

EMPRESA = "11111111-1111-1111-1111-111111111111"
LOTE = "22222222-2222-2222-2222-222222222222"
CLAVE = "clave-secreta-de-prueba-9X"


class FalsoOdin(BaseHTTPRequestHandler):
    """Servidor local que imita la API del lote. El estado vive en la clase que lo arranca (self.server.estado)."""

    def log_message(self, *args):
        pass

    def responder(self, codigo, cuerpo=None, cabeceras=None):
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else b""
        self.send_response(codigo)
        for k, v in (cabeceras or {}).items():
            self.send_header(k, v)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(datos)))
        self.end_headers()
        self.wfile.write(datos)

    def leer(self):
        return self.rfile.read(int(self.headers.get("Content-Length", 0)))

    def do_GET(self):
        e, url = self.server.estado, urllib.parse.urlparse(self.path)
        e["peticiones"].append(("GET", url.path))
        base = f"/v1/admin/companies/{EMPRESA}/intake-batches"
        if url.path == "/v1/health":
            self.responder(200)
        elif url.path == "/v1/admin/companies":
            self.responder(200, [{"id": EMPRESA, "name": "Hermanos Ruiz", "code": "RUIZ", "capabilities": []}])
        elif url.path == base:
            self.responder(200, {"batches": e["lotes"]})
        elif url.path == f"{base}/{LOTE}":
            self.responder(200, {"id": LOTE, "state": "OPEN", "warnings": e["avisos"], "uploaded": 0})
        elif url.path == f"{base}/{LOTE}/files":
            pedidas = urllib.parse.parse_qs(url.query)["sha256"]
            e["consultas"].append(pedidas)
            self.responder(200, {"sha256": [h for h in pedidas if h in e["conocidas"]]})
        else:
            self.responder(404)

    def do_POST(self):
        e, cuerpo = self.server.estado, self.leer()
        e["peticiones"].append(("POST", self.path))
        base = f"/v1/admin/companies/{EMPRESA}/intake-batches"
        if self.path == "/v1/admin/session":
            e["sesion"] = json.loads(cuerpo)
            ok = e["sesion"]["password"] == CLAVE
            self.responder(200 if ok else 401, {"userId": "x"} if ok else {"detail": "no"}, {"Set-Cookie": "sid=abc; Path=/"})
        elif self.path == base:
            self.responder(201, {"id": LOTE, "state": "OPEN", "channel": "COMMAND"})
        elif self.path == f"{base}/{LOTE}/files":
            ruta = re.search(rb'name="path"\r\n\r\n(.*?)\r\n--', cuerpo, re.S).group(1).decode()
            e["subidos"].append(ruta)
            self.responder(201, {"outcome": "CREATED", "document": {}})
        elif self.path == f"{base}/{LOTE}/close-upload":
            e["cerrado"] = True
            self.responder(200, {"id": LOTE, "state": "READING"})
        else:
            self.responder(404)


class PrimeraCargaTest(unittest.TestCase):

    def setUp(self):
        self.servidor = HTTPServer(("127.0.0.1", 0), FalsoOdin)
        self.servidor.estado = {"peticiones": [], "lotes": [], "avisos": [], "conocidas": set(), "consultas": [],
                                "subidos": [], "cerrado": False}
        threading.Thread(target=self.servidor.serve_forever, daemon=True).start()
        self.addCleanup(self.servidor.server_close)
        self.addCleanup(self.servidor.shutdown)
        self.estado = self.servidor.estado
        self.api = dp.Api(f"http://127.0.0.1:{self.servidor.server_port}", "")
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.carpeta = Path(self.tmp.name) / "archivo"
        self.carpeta.mkdir()

    def crear(self, ruta, texto="hola"):
        f = self.carpeta / ruta
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(texto)
        return f

    def lanzar(self, respuestas=("s",), argumentos=(), clave=CLAVE):
        salida = io.StringIO()
        with mock.patch("getpass.getpass", return_value=clave) as pedir_clave, \
                contextlib.redirect_stdout(salida), contextlib.redirect_stderr(salida), \
                mock.patch("builtins.input", side_effect=["admin@ruiz.invalid", *respuestas]):
            try:
                codigo = pc.ejecutar(self.api, [str(self.carpeta), *argumentos], libre=10**12)
            except SystemExit as fin:
                codigo = fin.code
        self.pedir_clave = pedir_clave
        return codigo, salida.getvalue()

    # recorrer la carpeta

    def test_recorreSinOcultosNiEnlacesNiBasuraDelSistema(self):
        self.crear("Obras/2024/acta.pdf")
        self.crear("contrato.txt")
        for basura in (".oculto", "Obras/.DS_Store", "Thumbs.db", "desktop.ini", ".git/config"):
            self.crear(basura)
        (self.carpeta / "enlace.txt").symlink_to(self.carpeta / "contrato.txt")
        (self.carpeta / "enlace-carpeta").symlink_to(self.carpeta / "Obras")
        ficheros, saltados = pc.recorrer(self.carpeta)
        self.assertEqual(["Obras/2024/acta.pdf", "contrato.txt"], [ruta for _, ruta in ficheros])
        self.assertEqual([], saltados)

    def test_saltaConAvisoLoQueSuperaElLimiteDeSubida(self):
        self.crear("grande.bin", "x" * 11)
        self.crear("pequeno.txt")
        ficheros, saltados = pc.recorrer(self.carpeta, limite=10)
        self.assertEqual(["pequeno.txt"], [ruta for _, ruta in ficheros])
        self.assertEqual(["grande.bin"], [ruta for ruta, _ in saltados])

    # subir, saltar, reanudar

    def test_subeConSuRutaRelativaYCierraLaSubida(self):
        self.crear("A/uno.txt", "1")
        self.crear("dos.txt", "2")
        codigo, texto = self.lanzar()
        self.assertEqual(0, codigo, texto)
        self.assertEqual(["A/uno.txt", "dos.txt"], self.estado["subidos"])
        self.assertTrue(self.estado["cerrado"])
        self.assertIn("Primera carga", texto)

    def test_saltaLasHuellasQueYaEstan(self):
        self.crear("uno.txt", "1")
        self.crear("dos.txt", "2")
        self.estado["conocidas"] = {hashlib.sha256(b"1").hexdigest()}
        codigo, texto = self.lanzar()
        self.assertEqual(0, codigo, texto)
        self.assertEqual(["dos.txt"], self.estado["subidos"])

    def test_preguntaLasHuellasPorTandas(self):
        for i in range(pc.TANDA + 1):
            self.crear(f"f{i:03}.txt", str(i))
        self.lanzar()
        self.assertEqual([pc.TANDA, 1], [len(c) for c in self.estado["consultas"]])

    def test_reutilizaUnLoteAbierto(self):
        self.estado["lotes"] = [{"id": LOTE, "state": "OPEN", "channel": "COMMAND"}]
        self.crear("uno.txt")
        codigo, texto = self.lanzar()
        self.assertEqual(0, codigo, texto)
        self.assertNotIn(("POST", f"/v1/admin/companies/{EMPRESA}/intake-batches"), self.estado["peticiones"])
        self.assertEqual(["uno.txt"], self.estado["subidos"])

    def test_seNiegaSiYaHayUnaPrimeraCargaEnLectura(self):
        self.estado["lotes"] = [{"id": LOTE, "state": "READING", "channel": "COMMAND"}]
        self.crear("uno.txt")
        codigo, texto = self.lanzar()
        self.assertIn("en curso", str(codigo))
        self.assertEqual([], self.estado["subidos"])

    # confirmación

    def test_sinConfirmarNoSubeNada(self):
        self.crear("uno.txt")
        codigo, texto = self.lanzar(respuestas=("n",))
        self.assertEqual(1, codigo)
        self.assertEqual([], self.estado["subidos"])
        self.assertFalse(self.estado["cerrado"])

    def test_conSiNoPregunta(self):
        self.crear("uno.txt")
        codigo, texto = self.lanzar(respuestas=(), argumentos=("--si",))
        self.assertEqual(0, codigo, texto)
        self.assertEqual(["uno.txt"], self.estado["subidos"])

    # contraseña

    def test_laContrasenaSePideConGetpassYNuncaSeEscribe(self):
        self.crear("uno.txt")
        with mock.patch.object(Path, "write_text") as escribir, mock.patch.object(Path, "write_bytes") as bytes_:
            codigo, texto = self.lanzar()
        self.assertEqual(0, codigo, texto)
        self.pedir_clave.assert_called_once()
        self.assertEqual(CLAVE, self.estado["sesion"]["password"])
        self.assertNotIn(CLAVE, texto)
        escribir.assert_not_called()
        bytes_.assert_not_called()

    def test_conContrasenaIncorrectaNoSubeNadaYNoLaRepite(self):
        self.crear("uno.txt")
        codigo, texto = self.lanzar(clave="otra-clave-9Z")
        self.assertIn("no son válidos", str(codigo))
        self.assertNotIn("otra-clave-9Z", texto + str(codigo))
        self.assertEqual([], self.estado["subidos"])

    # avisos de partida

    def test_avisaSinLectorSinModeloYConPocoEspacio(self):
        self.estado["avisos"] = [{"code": "NO_READER", "message": "Sin lector OCR."},
                                 {"code": "NO_LOCAL_MODEL", "message": "Sin modelo local."}]
        self.crear("uno.txt", "x" * 100)
        avisos = pc.avisos_de_partida(self.api, EMPRESA, LOTE, tamano=100, libre=150)
        self.assertEqual(3, len(avisos))
        self.assertTrue(any("Sin lector OCR" in a for a in avisos))
        self.assertTrue(any("espacio" in a.lower() for a in avisos))
        self.assertEqual([], pc.avisos_de_partida(self.api, EMPRESA, LOTE, tamano=1, libre=10**9)[2:])

    def test_elResumenEnseñaLosAvisosAntesDePreguntar(self):
        self.estado["avisos"] = [{"code": "NO_READER", "message": "Sin lector OCR."}]
        self.crear("uno.txt")
        codigo, texto = self.lanzar(respuestas=("n",))
        self.assertIn("Hermanos Ruiz", texto)
        self.assertIn("Sin lector OCR", texto)


if __name__ == "__main__":
    unittest.main()
