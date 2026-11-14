"""Pruebas de `make banco-cargar` con EXPEDIENTES: `cargar.py` y `cargar_expedientes.py` son falsos, en un directorio
temporal (EXP=...), y anotan sus llamadas. Nunca hay Docker ni red."""
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
FAKE = """import os, sys
with open(os.environ["FAKE_LOG"], "a", encoding="utf-8") as log:
    log.write(os.path.basename(sys.argv[0]) + " " + " ".join(sys.argv[1:]) + "\\n")
"""


class BancoCargarTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.exp = Path(tmp.name) / "exp"
        (self.exp / "pila").mkdir(parents=True)
        (self.exp / "datos").mkdir()
        (self.exp / "datos" / "manifiesto.json").write_text("{}", encoding="utf-8")
        (self.exp / "pila" / "cargar.py").write_text(FAKE, encoding="utf-8")
        self.log = Path(tmp.name) / "llamadas.txt"

    def add_loader(self):
        (self.exp / "pila" / "cargar_expedientes.py").write_text(FAKE, encoding="utf-8")

    def add_expedientes_json(self):
        (self.exp / "datos" / "expedientes.json").write_text("[]", encoding="utf-8")

    def make(self, *args):
        env = {**os.environ, "FAKE_LOG": str(self.log)}
        return subprocess.run(["make", "-s", "banco-cargar", f"EXP={self.exp}", *args], cwd=REPO, env=env,
                              capture_output=True, text=True)

    def calls(self):
        return self.log.read_text(encoding="utf-8").splitlines() if self.log.exists() else []

    def test_default_loads_only_the_manifest(self):
        self.add_loader()
        self.add_expedientes_json()
        result = self.make()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(self.calls()), 1)
        self.assertTrue(self.calls()[0].startswith("cargar.py --manifiesto"))

    def test_empty_expedientes_variable_is_the_default(self):
        self.add_loader()
        self.add_expedientes_json()
        result = self.make("EXPEDIENTES=")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(self.calls()), 1)

    def test_expedientes_load_after_the_manifest(self):
        self.add_loader()
        self.add_expedientes_json()
        result = self.make("EXPEDIENTES=1")
        self.assertEqual(result.returncode, 0, result.stderr)
        first, second = self.calls()
        self.assertTrue(first.startswith("cargar.py --manifiesto"))
        self.assertEqual(second, f"cargar_expedientes.py --mapa {REPO / 'banco' / 'estado' / 'carga.json'} "
                                 f"--datos {self.exp / 'datos'} --marcar-casos")

    def test_missing_loader_is_refused_before_loading_anything(self):
        self.add_expedientes_json()
        result = self.make("EXPEDIENTES=1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("cargar_expedientes.py, que no existe", result.stdout + result.stderr)
        self.assertEqual(self.calls(), [])

    def test_expedientes_without_expedientes_json_fails_before_loading(self):
        self.add_loader()
        result = self.make("EXPEDIENTES=1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(f"sin expedientes.json en {self.exp / 'datos'}", result.stdout + result.stderr)
        self.assertEqual(self.calls(), [])

    def test_failing_manifest_load_fails_the_target(self):
        self.add_loader()
        self.add_expedientes_json()
        (self.exp / "pila" / "cargar.py").write_text("import sys\nsys.exit(3)\n", encoding="utf-8")
        result = self.make("EXPEDIENTES=1")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.calls(), [])


class BancoPropietarioTest(unittest.TestCase):
    """D-099: arriba, cargar y abajo paran antes que nada si la pila apunta al propietario. Con BANCO_ENV en la orden de
    make (la variable literal del Makefile no se hereda del entorno); los objetivos llaman a un pila.sh y cargar.py falsos."""

    BANCO_PROPIETARIO = ("PILA_PROYECTO=odin-banco PILA_ESTADO=/nonexistent ODIN_API_PORT=48080 "
                         "ODIN_PANEL_PORT=48088 ODIN_MCP_PORT=8090")
    BANCO_PROYECTO_ODIN = "PILA_PROYECTO=odin PILA_ESTADO=/nonexistent ODIN_API_PORT=48080 ODIN_PANEL_PORT=48088 ODIN_MCP_PORT=48090"
    OBJETIVOS = ("banco-arriba", "banco-cargar", "banco-abajo")

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.exp = Path(tmp.name) / "exp"
        (self.exp / "pila").mkdir(parents=True)
        (self.exp / "datos").mkdir()
        (self.exp / "datos" / "manifiesto.json").write_text("{}", encoding="utf-8")
        (self.exp / "datos" / "expedientes.json").write_text("[]", encoding="utf-8")
        for nombre in ("pila.sh", "cargar.py"):
            ruta = self.exp / "pila" / nombre
            ruta.write_text("#!/usr/bin/env python3\n" + FAKE, encoding="utf-8")
            os.chmod(ruta, 0o755)
        self.log = Path(tmp.name) / "llamadas.txt"

    def run_make(self, target, banco_env):
        return subprocess.run(["make", "-s", target, f"EXP={self.exp}", f"BANCO_ENV={banco_env}"], cwd=REPO,
                              env={**os.environ, "FAKE_LOG": str(self.log)}, capture_output=True, text=True)

    def calls(self):
        return self.log.read_text(encoding="utf-8").splitlines() if self.log.exists() else []

    def test_owner_mcp_port_stops_every_target_before_the_stack(self):
        for target in self.OBJETIVOS:
            with self.subTest(target=target):
                result = self.run_make(target, self.BANCO_PROPIETARIO)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("No se ha cambiado nada.", result.stdout + result.stderr)
                self.assertEqual(self.calls(), [])

    def test_project_odin_stops_every_target_before_the_stack(self):
        for target in self.OBJETIVOS:
            with self.subTest(target=target):
                result = self.run_make(target, self.BANCO_PROYECTO_ODIN)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("No se ha cambiado nada.", result.stdout + result.stderr)
                self.assertEqual(self.calls(), [])

    def test_bench_ports_run_the_stack_as_before(self):
        bench = ("PILA_PROYECTO=odin-banco PILA_ESTADO=/nonexistent ODIN_API_PORT=48080 "
                 "ODIN_PANEL_PORT=48088 ODIN_MCP_PORT=48090")
        for target in self.OBJETIVOS:
            with self.subTest(target=target):
                result = self.run_make(target, bench)
                self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.calls(), ["pila.sh up", "cargar.py --manifiesto " + str(self.exp / "datos" / "manifiesto.json")
                                        + " --corpus " + str(self.exp / "datos" / "corpus") + " --marcar", "pila.sh down"])

if __name__ == "__main__":
    unittest.main()
