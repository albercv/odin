import contextlib
import hashlib
import io
import json
import os
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
            self.responder(200, {"id": LOTE, "state": "OPEN", "warnings": e["avisos"],
                                 "uploaded": e.get("ya_en_lote", 0) + len(e["subidos"])})
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
        elif self.path == f"{base}/{LOTE}/files" and e.get("fallar_subida"):
            self.responder(500, {"detail": "disco lleno"})
        elif self.path == f"{base}/{LOTE}/files":
            ruta = re.search(rb'name="path"\r\n\r\n(.*?)\r\n--', cuerpo, re.S).group(1).decode()
            if ruta.endswith(".pptx"):
                return self.responder(415, {"detail": "Ese formato no se admite"})
            if ruta.endswith(".enorme"):
                return self.responder(413, {"detail": "El fichero supera el límite"})
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

    def test_saltaLosVaciosYLosNombresConSaltoDeLineaSinRomper(self):
        self.crear("vacio.txt", "")
        self.crear("mal\nnombre.txt")
        self.crear("bien.txt")
        ficheros, saltados = pc.recorrer(self.carpeta)
        self.assertEqual(["bien.txt"], [ruta for _, ruta in ficheros])
        self.assertEqual({"vacio.txt": "está vacío", "mal\nnombre.txt": "el nombre tiene un salto de línea"},
                         dict(saltados))

    def test_saltaLosNombresQueNoSonUtf8SinRomper(self):
        self.crear("bien.txt")
        malo = "mal\udcff.txt"  # un byte que no es UTF-8, tal como lo da os.walk en Linux
        with mock.patch("os.walk", return_value=[(str(self.carpeta), [], ["bien.txt", malo])]):
            ficheros, saltados = pc.recorrer(self.carpeta)
        self.assertEqual(["bien.txt"], [ruta for _, ruta in ficheros])
        self.assertEqual([(malo, "el nombre no está en UTF-8")], saltados)
        self.assertEqual("mal\\udcff.txt", pc.mostrar(malo))

    # subir, saltar, reanudar

    def test_unFicheroQueLaApiNoAdmiteSeSaltaConSuMotivoYSigue(self):
        self.crear("hoja.pptx")
        self.crear("plano.enorme")
        self.crear("dos.txt", "2")
        self.crear("vacio.txt", "")
        codigo, texto = self.lanzar()
        self.assertEqual(0, codigo, texto)
        self.assertEqual(["dos.txt"], self.estado["subidos"])
        self.assertTrue(self.estado["cerrado"])
        self.assertIn("Se salta: hoja.pptx: Ese formato no se admite", texto)
        self.assertIn("Se salta: plano.enorme: El fichero supera el límite", texto)
        final = texto[texto.rindex("Saltados"):]
        for ruta in ("hoja.pptx", "plano.enorme", "vacio.txt"):
            self.assertIn(ruta, final)

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

    def test_sinNadaNuevoNoCierraLaSubidaYElLoteVacioSeReutiliza(self):
        self.crear("uno.txt", "1")
        self.estado["conocidas"] = {hashlib.sha256(b"1").hexdigest()}
        codigo, texto = self.lanzar()
        self.assertEqual(0, codigo, texto)
        self.assertEqual([], self.estado["subidos"])
        self.assertFalse(self.estado["cerrado"])
        self.assertIn("No hay nada nuevo que subir", texto)

    def test_unLoteReanudadoConDocumentosSeCierraAunqueNoHayaNadaNuevo(self):
        self.estado["lotes"] = [{"id": LOTE, "state": "OPEN", "channel": "COMMAND"}]
        self.estado["ya_en_lote"] = 2
        self.crear("uno.txt", "1")
        self.estado["conocidas"] = {hashlib.sha256(b"1").hexdigest()}
        codigo, texto = self.lanzar()
        self.assertEqual(0, codigo, texto)
        self.assertTrue(self.estado["cerrado"])

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
        self.assertIn("leyéndose", str(codigo))
        self.assertEqual([], self.estado["subidos"])

    def test_unaCargaEnRevisionNoBloqueaYElResumenLoAvisa(self):
        self.estado["lotes"] = [{"id": "otro", "state": "IN_REVIEW", "channel": "COMMAND"}]
        self.crear("uno.txt")
        codigo, texto = self.lanzar()
        self.assertEqual(0, codigo, texto)
        self.assertIn(("POST", f"/v1/admin/companies/{EMPRESA}/intake-batches"), self.estado["peticiones"])
        self.assertEqual(["uno.txt"], self.estado["subidos"])
        self.assertIn("Hay otra primera carga pendiente de revisar en el panel; esta se revisa aparte.", texto)

    def test_unaCargaEnRevisionNoImpideReutilizarElLoteAbierto(self):
        self.estado["lotes"] = [{"id": LOTE, "state": "OPEN", "channel": "COMMAND"},
                                {"id": "otro", "state": "IN_REVIEW", "channel": "COMMAND"}]
        self.crear("uno.txt")
        codigo, texto = self.lanzar()
        self.assertEqual(0, codigo, texto)
        self.assertNotIn(("POST", f"/v1/admin/companies/{EMPRESA}/intake-batches"), self.estado["peticiones"])
        self.assertIn("esta se revisa aparte", texto)

    def test_unaCargaCerradaConPendientesNoBloqueaYTambienSeAvisa(self):
        self.estado["lotes"] = [{"id": "otro", "state": "CLOSED", "channel": "COMMAND"}]
        self.crear("uno.txt")
        codigo, texto = self.lanzar()
        self.assertEqual(0, codigo, texto)
        self.assertIn("esta se revisa aparte", texto)

    def test_sinOtraCargaPendienteNoHayAviso(self):
        self.crear("uno.txt")
        codigo, texto = self.lanzar()
        self.assertNotIn("esta se revisa aparte", texto)

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

    # errores de la API

    def lanzar_main(self, *argumentos):
        salida = io.StringIO()
        entorno = {"PRIMERA_CARGA_API_URL": self.api.url}
        with mock.patch.object(pc.dp, "leer_entorno", return_value={}), mock.patch.dict(os.environ, entorno), \
                mock.patch("sys.argv", ["primera_carga.py", str(self.carpeta), *argumentos]), \
                mock.patch("getpass.getpass", return_value=CLAVE), \
                mock.patch("builtins.input", side_effect=["admin@ruiz.invalid", "s"]), \
                contextlib.redirect_stdout(salida), contextlib.redirect_stderr(salida):
            codigo = pc.main()
        return codigo, salida.getvalue()

    def test_siLaApiFallaAMitadDeLaSubidaDiceQueHacerSinTraza(self):
        self.crear("uno.txt")
        self.estado["fallar_subida"] = True
        codigo, texto = self.lanzar_main()
        self.assertEqual(1, codigo)
        self.assertIn("Vuelve a lanzar la misma orden", texto)
        self.assertIn("HTTP 500", texto)
        self.assertNotIn("Traceback", texto)
        self.assertNotIn(CLAVE, texto)

    def test_laOrdenUsaPrimeraCargaApiUrlYNoOdinApiUrl(self):
        self.crear("uno.txt")
        with mock.patch.dict(os.environ, {"ODIN_API_URL": "http://127.0.0.1:1"}):
            codigo, texto = self.lanzar_main()
        self.assertEqual(0, codigo, texto)

    # D-099: la guarda del propietario (nunca se conecta: conectar está sustituido)

    def guarda(self, url, *argumentos, **variables):
        entorno = {k: v for k, v in os.environ.items() if k not in ("INSTALACION_PROYECTO", "CONFIRMAR_INSTALACION")}
        entorno |= {"PRIMERA_CARGA_API_URL": url, **variables} if url else variables
        if not url:
            entorno.pop("PRIMERA_CARGA_API_URL", None)
        conectar = mock.Mock(side_effect=RuntimeError("conectado"))
        salida = io.StringIO()
        with mock.patch.object(pc.dp, "leer_entorno", return_value={}), mock.patch.dict(os.environ, entorno, clear=True), \
                mock.patch.object(pc.dp, "conectar", conectar), \
                mock.patch("sys.argv", ["primera_carga.py", str(self.carpeta), *argumentos]), \
                contextlib.redirect_stdout(salida), contextlib.redirect_stderr(salida):
            try:
                pc.main()
                return None, conectar.called
            except SystemExit as fin:
                return str(fin.code), conectar.called

    def test_unaPilaDeEnsayoNoVaALosPuertosDelPropietario(self):
        for puerto in sorted(dp.PUERTOS_PROPIETARIO):
            motivo, conectada = self.guarda(f"http://127.0.0.1:{puerto}", INSTALACION_PROYECTO="odin-prueba")
            self.assertIn("puertos de la instalación en uso", motivo)
            self.assertFalse(conectada)

    def test_conSiHaciaElPropietarioPideConfirmarInstalacion(self):
        for url in ("http://127.0.0.1:8080", None):  # None: el puerto por defecto de deploy/.env
            motivo, conectada = self.guarda(url, "--si")
            self.assertIn("CONFIRMAR_INSTALACION=ESCRIBIR", motivo)
            self.assertFalse(conectada)
        motivo, conectada = self.guarda("http://127.0.0.1:8080", "--si", CONFIRMAR_INSTALACION="ESCRIBIR")
        self.assertIsNone(motivo)
        self.assertTrue(conectada)

    def test_elCaminoInteractivoHaciaElPropietarioNoCambia(self):
        motivo, conectada = self.guarda("http://127.0.0.1:8080")
        self.assertIsNone(motivo)
        self.assertTrue(conectada)

    def test_unaPilaDeEnsayoEnSusPuertosSigueConSi(self):
        motivo, conectada = self.guarda("http://127.0.0.1:48080", "--si", INSTALACION_PROYECTO="odin-primera-carga")
        self.assertIsNone(motivo)
        self.assertTrue(conectada)


if __name__ == "__main__":
    unittest.main()
