"""Pruebas de la protección de la instalación del propietario (D-099): el banco nunca usa la pila «odin» ni los
puertos 8080, 8088 y 8090. Sin red y sin Docker: solo funciones puras y la orden de línea de comandos."""
import os
import subprocess
import sys
import unittest
from pathlib import Path
from unittest import mock

from banco import propietario
from banco.propietario import PropietarioError, proteger_banco, proteger_propietario

BANCO = Path(__file__).resolve().parent
SIN_PILA_ODIN = {k: v for k, v in os.environ.items() if not k.startswith(("ODIN_", "PILA_", "CONFIRMAR_"))}


class PropietarioErrorTest(unittest.TestCase):
    def test_is_a_system_exit_with_the_message(self):
        error = PropietarioError("algo. No se ha cambiado nada.")
        self.assertIsInstance(error, SystemExit)
        self.assertEqual(str(error), "algo. No se ha cambiado nada.")


class ProtegerPropietarioTest(unittest.TestCase):
    def test_other_project_on_owner_port_from_environment_is_refused(self):
        with self.assertRaisesRegex(PropietarioError, r"propietario \(\['8090'\]\)"):
            proteger_propietario({"ODIN_API_PORT": "48080", "ODIN_PANEL_PORT": "48088", "ODIN_MCP_PORT": "8090"},
                                 "odin-banco", escribe=True)

    def test_owner_port_in_url_is_refused_even_if_environment_is_clean(self):
        with self.assertRaisesRegex(PropietarioError, r"\(\['8080'\]\)"):
            proteger_propietario({"ODIN_API_PORT": "48080", "ODIN_PANEL_PORT": "48088", "ODIN_MCP_PORT": "48090"},
                                 "odin-banco", True, urls=("http://127.0.0.1:8080/v1",))

    def test_defaults_of_the_owner_ports_apply_when_missing(self):
        with self.assertRaisesRegex(PropietarioError, "No se ha cambiado nada."):
            proteger_propietario({}, "otra", escribe=False)

    def test_bench_ports_and_other_urls_pass(self):
        proteger_propietario({"ODIN_API_PORT": "48080", "ODIN_PANEL_PORT": "48088", "ODIN_MCP_PORT": "48090"},
                             "odin-banco", True, urls=("http://127.0.0.1:48090/mcp", "http://example.org:9/x"))

    def test_url_without_port_is_ignored(self):
        proteger_propietario({"ODIN_API_PORT": "28080", "ODIN_PANEL_PORT": "28088", "ODIN_MCP_PORT": "28090"},
                             "odin-medicion", True, urls=("http://127.0.0.1/mcp",))

    def test_reading_the_owner_project_needs_no_confirmation(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            proteger_propietario({}, "odin", escribe=False, urls=("http://127.0.0.1:8090/mcp",))

    def test_writing_the_owner_project_needs_confirmation(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(PropietarioError, "CONFIRMAR_INSTALACION=PROPIETARIO"):
                proteger_propietario({}, "odin", escribe=True)

    def test_wrong_confirmation_value_is_refused(self):
        with mock.patch.dict(os.environ, {"CONFIRMAR_INSTALACION": "si"}, clear=True):
            with self.assertRaisesRegex(PropietarioError, "No se ha cambiado nada."):
                proteger_propietario({}, "odin", escribe=True)

    def test_right_confirmation_lets_the_owner_write(self):
        with mock.patch.dict(os.environ, {"CONFIRMAR_INSTALACION": "PROPIETARIO"}, clear=True):
            proteger_propietario({}, "odin", escribe=True)


class ProtegerBancoTest(unittest.TestCase):
    def test_the_owner_project_is_refused_even_for_reading(self):
        with self.assertRaisesRegex(PropietarioError, "nunca la pila «odin»"):
            proteger_banco({"PILA_PROYECTO": "odin"}, urls=())

    def test_bench_defaults_pass(self):
        proteger_banco({})

    def test_owner_port_in_environment_is_refused(self):
        with self.assertRaisesRegex(PropietarioError, "No se ha cambiado nada."):
            proteger_banco({"ODIN_MCP_PORT": "8090"})

    def test_owner_port_in_url_is_refused(self):
        with self.assertRaisesRegex(PropietarioError, r"\['8090'\]"):
            proteger_banco({}, urls=("http://127.0.0.1:8090/mcp",))

    def test_empty_values_fall_back_to_the_bench_defaults(self):
        proteger_banco({"ODIN_MCP_PORT": "", "PILA_PROYECTO": ""})

    def test_reads_the_process_environment_by_default(self):
        with mock.patch.dict(os.environ, {"ODIN_API_PORT": "8080"}, clear=True):
            with self.assertRaises(PropietarioError):
                proteger_banco()


class CliTest(unittest.TestCase):
    def run_cli(self, env):
        return subprocess.run([sys.executable, str(BANCO / "propietario.py")], env={**env, "PYTHONDONTWRITEBYTECODE": "1"},
                              capture_output=True, text=True, cwd=BANCO.parent)

    def test_exits_zero_with_bench_defaults(self):
        result = self.run_cli(SIN_PILA_ODIN)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_exits_one_and_says_so_on_the_owner_port(self):
        result = self.run_cli({**SIN_PILA_ODIN, "ODIN_MCP_PORT": "8090"})
        self.assertEqual(result.returncode, 1)
        self.assertIn("No se ha cambiado nada.", result.stderr)


if __name__ == "__main__":
    unittest.main()
