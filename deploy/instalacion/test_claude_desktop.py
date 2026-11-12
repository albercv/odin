import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

import claude_desktop as cd

CREDENCIALES = {"api": "http://127.0.0.1:8080",
                "trabajadores": {"marta": {"nombre": "Marta Olivares Ríos", "empresa": "Administraciones Ribera",
                                           "clave": "odin_secreta_marta"},
                                 "javier": {"nombre": "Javier Bermúdez Lara", "empresa": "Administraciones Ribera",
                                            "clave": "odin_secreta_javier"}}}


class RegistrarTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        self.config = self.dir / "Claude" / "claude_desktop_config.json"
        self.copias = self.dir / "copias"
        self.credenciales = self.dir / "credenciales.local.json"
        self.credenciales.write_text(json.dumps(CREDENCIALES))

    def tearDown(self):
        self.tmp.cleanup()

    def registrar(self, trabajador="marta"):
        salida = io.StringIO()
        with contextlib.redirect_stdout(salida):
            cd.registrar(self.config, self.credenciales, trabajador, Path("/repo/odin"), "/usr/local/bin/node",
                         self.copias)
        return salida.getvalue()

    def test_anadeOdinSinTocarLasDemasEntradas(self):
        self.config.parent.mkdir(parents=True)
        self.config.write_text(json.dumps({"mcpServers": {"otro": {"command": "x"}}, "preferencias": {"a": 1}}))
        self.registrar()
        config = json.loads(self.config.read_text())
        self.assertEqual({"command": "x"}, config["mcpServers"]["otro"])
        self.assertEqual({"a": 1}, config["preferencias"])
        self.assertEqual({"command": "/usr/local/bin/node", "args": ["/repo/odin/mcp/dist/stdio.js"],
                          "env": {"ODIN_API_URL": "http://127.0.0.1:8080", "ODIN_ACCESS_KEY": "odin_secreta_marta"}},
                         config["mcpServers"]["odin"])

    def test_cambiarDeTrabajadorSustituyeLaEntrada(self):
        self.registrar("marta")
        self.registrar("javier")
        config = json.loads(self.config.read_text())
        self.assertEqual("odin_secreta_javier", config["mcpServers"]["odin"]["env"]["ODIN_ACCESS_KEY"])

    def test_creaElFicheroSiNoExiste(self):
        self.registrar()
        self.assertIn("odin", json.loads(self.config.read_text())["mcpServers"])

    def test_copiaPreviaDelFichero(self):
        self.config.parent.mkdir(parents=True)
        original = json.dumps({"mcpServers": {"otro": {"command": "x"}}})
        self.config.write_text(original)
        self.registrar()
        copias = list(self.copias.glob("claude_desktop_config-*.json"))
        self.assertEqual(1, len(copias))
        self.assertEqual(original, copias[0].read_text())

    def test_unFicheroQueNoEsJsonNoSeToca(self):
        self.config.parent.mkdir(parents=True)
        self.config.write_text("{ roto")
        with self.assertRaises(SystemExit):
            self.registrar()
        self.assertEqual("{ roto", self.config.read_text())

    def test_trabajadorDesconocidoDiceLosValidos(self):
        with self.assertRaises(SystemExit) as error:
            self.registrar("pepe")
        self.assertIn("javier, marta", str(error.exception))

    def test_nuncaEscribeLaClaveEnPantalla(self):
        salida = self.registrar()
        self.assertNotIn("odin_secreta_marta", salida)
        self.assertIn("Marta Olivares Ríos", salida)
        self.assertIn("Reinicia Claude Desktop", salida)


if __name__ == "__main__":
    unittest.main()
