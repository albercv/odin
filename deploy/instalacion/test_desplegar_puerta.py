import os
import shutil
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
FICHEROS = ("desplegar.sh", "comun.sh", "puerta.py")


class DesplegarPuertaTest(unittest.TestCase):
    """Copia los guiones a un repositorio temporal (comun.sh opera sobre el repositorio donde viven) con docker y gh falsos."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        raiz = Path(self.tmp.name)
        self.repo, self.bin = raiz / "repo", raiz / "bin"
        (self.repo / "deploy/instalacion").mkdir(parents=True)
        self.bin.mkdir()
        for f in FICHEROS:
            shutil.copy(AQUI / f, self.repo / "deploy/instalacion" / f)
        for c in (["init", "-q", "-b", "develop"], ["-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "a"]):
            subprocess.run(["git", *c], cwd=self.repo, check=True, capture_output=True)
        self.env_file = raiz / "env-prueba"
        self.env_file.write_text("ODIN_DB_OWNER_USER=x\n")
        self.llamadas = raiz / "llamadas"
        for nombre, codigo in (("docker", 1), ("gh", 1)):
            self.falso(nombre, codigo)

    def falso(self, nombre, codigo, salida=""):
        f = self.bin / nombre
        f.write_text(f"#!/bin/sh\necho {nombre} \"$@\" >> '{self.llamadas}'\necho '{salida}'\nexit {codigo}\n")
        f.chmod(f.stat().st_mode | stat.S_IXUSR)

    def lanzar(self, extra=None, confirmar=True):
        env = {"PATH": f"{self.bin}:/usr/bin:/bin", "HOME": self.tmp.name, "INSTALACION_PROYECTO": "odin",
               "INSTALACION_ENV": str(self.env_file)} | (extra or {})
        if confirmar:
            env["CONFIRMAR"] = "ACTUALIZAR"
        return subprocess.run(["bash", str(self.repo / "deploy/instalacion/desplegar.sh")], cwd=self.repo, env=env | {"MODO": "actualizar"},
                              capture_output=True, text=True)

    def sin_cambios(self, r):
        self.assertNotEqual(r.returncode, 0, r.stdout)
        self.assertIn("No se ha cambiado nada.", r.stderr)
        self.assertFalse(self.llamadas.exists(), self.llamadas.read_text() if self.llamadas.exists() else "")
        self.assertFalse((self.repo / "data").exists())

    def test_sin_rama_se_niega_antes_de_tocar_nada(self):
        r = self.lanzar()
        self.assertIn("ODIN_RAMA_INSTALACION", r.stderr)
        self.sin_cambios(r)

    def test_puerta_cerrada_se_niega_antes_de_la_copia(self):
        r = self.lanzar({"RAMA": "develop"})
        self.assertIn("remote.origin.url", r.stderr)
        self.sin_cambios(r)

    def test_construir_no_se_niega_en_odin(self):
        r = self.lanzar({"CONSTRUIR": "no", "RAMA": "develop"})
        self.assertIn("CONSTRUIR=no", r.stderr)
        self.sin_cambios(r)

    def test_explicacion_muestra_el_veredicto_y_no_cambia_nada(self):
        r = self.lanzar({"RAMA": "develop"}, confirmar=False)
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("La puerta se negaría", r.stdout)
        self.assertFalse((self.repo / "data").exists())

    def test_puerta_aprobada_sigue_adelante(self):
        puerta = self.repo / "deploy/instalacion/puerta.py"
        puerta.write_text("import sys\nprint('a' * 40)\n")
        r = self.lanzar({"RAMA": "develop"})
        self.assertIn("no está en marcha", r.stderr)


if __name__ == "__main__":
    unittest.main()
