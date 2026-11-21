import shutil
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
FICHEROS = ("desplegar.sh", "recuento.sh", "comun.sh")
GENERADOR_OK = """import sys
from pathlib import Path
Path(sys.argv[0]).with_name("generado").open("a").write("x")
d = Path(sys.argv[0]).resolve().parents[1] / "datos"
d.mkdir(exist_ok=True)
(d / "manifiesto.json").write_text("{}")
(d / "claves.json").write_text("[]")
"""


class ArchivoFicticioTest(unittest.TestCase):
    """Guiones copiados a un repositorio temporal, con docker y gh falsos y un generador falso del archivo ficticio."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        raiz = Path(self.tmp.name)
        self.repo, self.bin = raiz / "repo", raiz / "bin"
        (self.repo / "deploy/instalacion").mkdir(parents=True)
        (self.repo / "datos-prueba/archivo-ficticio/generar").mkdir(parents=True)
        self.bin.mkdir()
        for f in FICHEROS:
            shutil.copy(AQUI / f, self.repo / "deploy/instalacion" / f)
        self.generador = self.repo / "datos-prueba/archivo-ficticio/generar/generar.py"
        self.generador.write_text(GENERADOR_OK)
        self.datos = self.repo / "datos-prueba/archivo-ficticio/datos"
        self.generado = self.generador.with_name("generado")
        (self.repo / "deploy/instalacion/puerta.py").write_text("print('a' * 40)\n")
        for c in (["init", "-q", "-b", "develop"], ["-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "a"]):
            subprocess.run(["git", *c], cwd=self.repo, check=True, capture_output=True)
        self.env_file = raiz / "env-prueba"
        self.env_file.write_text("ODIN_DB_OWNER_USER=x\nODIN_DB_NAME=y\n")
        self.llamadas = raiz / "llamadas"
        for nombre in ("docker", "gh"):
            f = self.bin / nombre
            f.write_text(f"#!/bin/sh\necho {nombre} \"$@\" >> '{self.llamadas}'\nexit 1\n")
            f.chmod(f.stat().st_mode | stat.S_IXUSR)

    def env(self, **extra):
        return {"PATH": f"{self.bin}:/usr/bin:/bin", "HOME": self.tmp.name, "INSTALACION_PROYECTO": "odin",
                "INSTALACION_ENV": str(self.env_file), "RAMA": "develop"} | extra

    def bash(self, *args, **extra):
        return subprocess.run(["bash", *args], cwd=self.repo, env=self.env(**extra), capture_output=True, text=True)

    def funcion(self):
        return self.bash("-c", ". deploy/instalacion/comun.sh; asegurar_archivo_ficticio")

    def test_genera_si_falta_y_no_regenera(self):
        r = self.funcion()
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertTrue((self.datos / "claves.json").exists())
        self.assertEqual(self.generado.read_text(), "x")
        self.assertEqual(self.funcion().returncode, 0)
        self.assertEqual(self.generado.read_text(), "x")

    def test_generador_que_falla_da_el_mensaje(self):
        self.generador.write_text("raise SystemExit(3)\n")
        r = self.funcion()
        self.assertNotEqual(r.returncode, 0)
        self.assertTrue(r.stderr.strip().endswith("No se ha cambiado nada."), r.stderr)

    def test_desplegar_lo_asegura_antes_de_tocar_la_instalacion(self):
        r = self.bash("deploy/instalacion/desplegar.sh", MODO="actualizar", CONFIRMAR="ACTUALIZAR")
        self.assertTrue((self.datos / "claves.json").exists(), r.stderr)
        self.assertIn("no está en marcha", r.stderr)
        self.assertNotIn(" exec ", self.llamadas.read_text())  # solo la pregunta de en_marcha, nada de copia
        self.assertFalse((self.repo / "data/copias").exists())

    def test_desplegar_con_generador_roto_no_toca_nada(self):
        self.generador.write_text("raise SystemExit(3)\n")
        r = self.bash("deploy/instalacion/desplegar.sh", MODO="actualizar", CONFIRMAR="ACTUALIZAR")
        self.assertNotEqual(r.returncode, 0)
        self.assertTrue(r.stderr.strip().endswith("No se ha cambiado nada."), r.stderr)
        self.assertFalse(self.llamadas.exists())

    def test_recuento_lo_asegura_el_primero(self):
        r = self.bash("deploy/instalacion/recuento.sh")
        self.assertTrue((self.datos / "claves.json").exists(), r.stderr)
        self.assertEqual(self.generado.read_text(), "x")

    def test_recuento_con_generador_roto_no_llega_a_la_base(self):
        self.generador.write_text("raise SystemExit(3)\n")
        r = self.bash("deploy/instalacion/recuento.sh")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("No se ha cambiado nada.", r.stderr)
        self.assertFalse(self.llamadas.exists())


if __name__ == "__main__":
    unittest.main()
