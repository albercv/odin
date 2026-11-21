import copy
import json
import os
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
PUERTA = AQUI / "puerta.py"
BASE = json.loads((AQUI / "datos_prueba_puerta" / "ejecuciones.json").read_text())["workflow_runs"][0]
FIN = "No se ha cambiado nada."


def git(cwd, *args):
    return subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()


class Entorno:
    """Repositorio temporal con remoto bare local y un gh falso al principio del PATH."""

    def __init__(self, rama="develop"):
        self.tmp = tempfile.TemporaryDirectory()
        raiz = Path(self.tmp.name)
        self.bare, self.repo, self.bin = raiz / "remoto.git", raiz / "repo", raiz / "bin"
        self.bin.mkdir()
        git(raiz, "init", "-q", "--bare", "-b", rama, str(self.bare))
        git(raiz, "init", "-q", "-b", rama, str(self.repo))
        git(self.repo, "remote", "add", "origin", "https://github.com/dueno/odin.git")
        git(self.repo, "config", "url." + str(self.bare) + ".insteadOf", "https://github.com/dueno/odin.git")
        git(self.repo, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "uno")
        git(self.repo, "push", "-q", "origin", rama)
        self.rama = rama
        self.sha = git(self.repo, "rev-parse", "HEAD")

    def gh(self, runs=None, salida=None, codigo=0):
        cuerpo = salida if salida is not None else json.dumps({"total_count": len(runs or []), "workflow_runs": runs or []})
        guion = self.bin / "gh"
        guion.write_text(f"#!/bin/sh\necho \"$@\" > '{self.bin}/args'\ncat <<'FIN'\n{cuerpo}\nFIN\nexit {codigo}\n")
        guion.chmod(guion.stat().st_mode | stat.S_IXUSR)

    def run(self, rama=None, extra_env=None, *argv, con_gh=True):
        path = f"{self.bin}:/usr/bin:/bin" if con_gh else "/usr/bin:/bin"
        env = {"PATH": path, "HOME": self.tmp.name} | (extra_env if extra_env is not None else {"RAMA": rama or self.rama})
        return subprocess.run([sys.executable, str(PUERTA), *argv], cwd=self.repo, env=env, capture_output=True, text=True)

    def ejecucion(self, **cambios):
        return copy.deepcopy(BASE) | {"head_sha": self.sha, "head_branch": self.rama} | cambios


class PuertaTest(unittest.TestCase):
    def setUp(self):
        self.e = Entorno()
        self.addCleanup(self.e.tmp.cleanup)

    def negada(self, r, trozo):
        self.assertEqual(r.returncode, 1, r.stderr)
        self.assertEqual(r.stdout, "")
        self.assertIn(trozo, r.stderr)
        self.assertTrue(r.stderr.strip().endswith(FIN), r.stderr)

    def test_sin_rama(self):
        self.e.gh([self.e.ejecucion()])
        self.negada(self.e.run(extra_env={}), "ODIN_RAMA_INSTALACION")

    def test_rama_invalida(self):
        self.e.gh([self.e.ejecucion()])
        self.negada(self.e.run(rama="feature/x"), "solo se instala de develop o de main")

    def test_rama_por_orden_manda_sobre_el_fichero(self):
        self.e.gh([self.e.ejecucion()])
        r = self.e.run(extra_env={"RAMA": "main", "ODIN_RAMA_INSTALACION": "develop"})
        self.negada(r, "main")

    def test_rama_del_fichero_si_no_hay_orden(self):
        self.e.gh([self.e.ejecucion()])
        r = self.e.run(extra_env={"RAMA": "", "ODIN_RAMA_INSTALACION": "develop"})
        self.assertEqual(r.stdout.strip(), self.e.sha, r.stderr)

    def test_sha_fuera_de_origin(self):
        git(self.e.repo, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "dos")
        self.e.gh([self.e.ejecucion()])
        self.negada(self.e.run(), "no está en origin/develop")

    def test_fetch_falla(self):
        git(self.e.repo, "remote", "set-url", "origin", "https://github.com/dueno/otro.git")
        git(self.e.repo, "config", "url." + str(self.e.bare.parent / "no-existe.git") + ".insteadOf", "https://github.com/dueno/otro.git")
        self.e.gh([self.e.ejecucion()])
        self.negada(self.e.run(), "git fetch")

    def test_gh_ausente(self):
        self.negada(self.e.run(con_gh=False), "gh")

    def test_gh_con_error(self):
        self.e.gh(salida="gh: no estás autenticado", codigo=4)
        self.negada(self.e.run(), "Ha fallado gh api")

    def test_json_invalido(self):
        self.e.gh(salida="esto no es json")
        self.negada(self.e.run(), "forma esperada")

    def test_json_sin_campos(self):
        self.e.gh(salida='{"workflow_runs": [{"head_sha": "x"}]}')
        self.negada(self.e.run(), "forma esperada")

    def test_sin_ejecuciones(self):
        self.e.gh([])
        self.negada(self.e.run(), "sin validar")

    def test_en_curso(self):
        self.e.gh([self.e.ejecucion(status="in_progress", conclusion=None)])
        self.negada(self.e.run(), "pendiente")

    def test_en_rojo_con_enlace(self):
        self.e.gh([self.e.ejecucion(conclusion="failure")])
        self.negada(self.e.run(), "en rojo (https://github.com/dueno/odin/actions/runs/1001)")

    def test_verde_de_otro_sha(self):
        self.e.gh([self.e.ejecucion(head_sha="0" * 40)])
        self.negada(self.e.run(), "sin validar")

    def test_verde_de_otra_rama(self):
        self.e.gh([self.e.ejecucion(head_branch="main")])
        self.negada(self.e.run(), "sin validar")

    def test_verde_de_pull_request_no_cuenta(self):
        self.e.gh([self.e.ejecucion(event="pull_request")])
        self.negada(self.e.run(), "sin validar")

    def test_la_mas_reciente_en_rojo_con_una_anterior_en_verde(self):
        self.e.gh([self.e.ejecucion(), self.e.ejecucion(conclusion="failure", id=1002, run_number=8,
                                                          created_at="2026-11-21T11:00:00Z")])
        self.negada(self.e.run(), "en rojo")

    def test_la_mas_reciente_en_verde_con_una_anterior_en_rojo(self):
        self.e.gh([self.e.ejecucion(conclusion="failure", id=1002, run_number=6, created_at="2026-11-21T09:00:00Z"),
                   self.e.ejecucion()])
        self.assertEqual(self.e.run().stdout.strip(), self.e.sha)

    def test_verde_aprueba_y_escribe_solo_el_sha(self):
        self.e.gh([self.e.ejecucion()])
        r = self.e.run()
        self.assertEqual((r.returncode, r.stdout, r.stderr), (0, self.e.sha + "\n", ""))
        args = (self.e.bin / "args").read_text()
        self.assertIn(f"repos/dueno/odin/actions/workflows/validacion.yml/runs?head_sha={self.e.sha}&event=push", args)

    def test_explicar_en_verde(self):
        self.e.gh([self.e.ejecucion()])
        r = self.e.run(None, None, "--explicar")
        self.assertEqual(r.returncode, 0)
        self.assertEqual(r.stdout.strip(), f"La puerta dejaría instalar {self.e.sha[:7]} de develop: "
                         "validación en verde (https://github.com/dueno/odin/actions/runs/1001)")

    def test_explicar_en_rojo_sale_con_cero(self):
        self.e.gh([self.e.ejecucion(conclusion="failure")])
        r = self.e.run(None, None, "--explicar")
        self.assertEqual(r.returncode, 0)
        self.assertTrue(r.stdout.startswith("La puerta se negaría: "), r.stdout)
        self.assertIn("en rojo", r.stdout)


if __name__ == "__main__":
    unittest.main()
