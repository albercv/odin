import contextlib
import io
import plistlib
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

import arranque_lector as al

MODEL = "models--mlx-community--Unlimited-OCR-2-8bit"
LABEL = "com.e2d.odin.puente-ocr"


class Fake:
    """Sustituye a launchctl, lsof, ps y kill; guarda lo llamado."""

    def __init__(self, listening="", command="", print_out="", print_code=0):
        self.calls = []
        self.listening, self.command = listening, command
        self.print_out, self.print_code = print_out, print_code

    def __call__(self, cmd):
        self.calls.append(cmd)
        if cmd[0] == "lsof":
            return 0, self.listening
        if cmd[0] == "ps":
            return 0, self.command
        if cmd[:2] == ["launchctl", "print"]:
            return self.print_code, self.print_out
        return 0, ""


class Base(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        self.lector = self.dir / "OCR" / ".worktrees" / "puente-odin"
        self.lector.mkdir(parents=True)
        (self.lector / "serve.py").write_text("")
        self.hf = self.dir / "OCR" / ".cache" / "huggingface"
        (self.hf / "hub" / MODEL).mkdir(parents=True)
        self.agents = self.dir / "LaunchAgents"
        self.log = self.dir / "odin" / "data" / "puente-ocr.log"
        self.plist = self.agents / f"{LABEL}.plist"

    def tearDown(self):
        self.tmp.cleanup()

    def cfg(self, **over):
        base = dict(lector=self.lector, uv="/opt/homebrew/bin/uv", agents=self.agents, log=self.log, uid=501,
                    confirmar="ARRANQUE")
        base.update(over)
        return al.Config(**base)

    def call(self, fn, *args):
        salida = io.StringIO()
        with contextlib.redirect_stdout(salida), contextlib.redirect_stderr(salida):
            try:
                code = fn(*args)
            except SystemExit as exit:
                code = 1 if isinstance(exit.code, str) else exit.code
                print(exit.code if isinstance(exit.code, str) else "")
        return code, salida.getvalue()


class PlistTest(Base):

    def test_contenidoDelPlist(self):
        d = plistlib.loads(al.build_plist(self.cfg(), self.hf))
        self.assertEqual(d["Label"], LABEL)
        self.assertEqual(d["ProgramArguments"], ["/opt/homebrew/bin/uv", "run", "python", "serve.py", "--quant", "8"])
        self.assertEqual(d["WorkingDirectory"], str(self.lector))
        env = d["EnvironmentVariables"]
        self.assertEqual((env["HF_HUB_OFFLINE"], env["UV_OFFLINE"], env["HF_HOME"]), ("1", "1", str(self.hf)))
        self.assertIn("/opt/homebrew/bin", env["PATH"].split(":"))
        self.assertIs(d["RunAtLoad"], True)
        self.assertIs(d["KeepAlive"], True)
        self.assertEqual(d["ThrottleInterval"], 30)
        self.assertEqual(d["StandardOutPath"], str(self.log))
        self.assertEqual(d["StandardErrorPath"], str(self.log))

    @unittest.skipUnless(shutil.which("plutil"), "solo macOS")
    def test_plutilLoDaPorBueno(self):
        self.agents.mkdir()
        self.plist.write_bytes(al.build_plist(self.cfg(), self.hf))
        r = subprocess.run(["plutil", "-lint", str(self.plist)], capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)


class InstalarTest(Base):

    def instalar(self, run, cfg=None, health=lambda: True):
        return self.call(al.instalar, cfg or self.cfg(), run, health, lambda s: None)

    def test_sinConfirmarNoEscribeNada(self):
        run = Fake()
        code, msg = self.instalar(run, self.cfg(confirmar=""))
        self.assertNotEqual(code, 0)
        self.assertIn("CONFIRMAR=ARRANQUE", msg)
        self.assertFalse(self.agents.exists())
        self.assertEqual(run.calls, [])

    def test_sinServePyNoEscribeNada(self):
        (self.lector / "serve.py").unlink()
        run = Fake()
        code, msg = self.instalar(run)
        self.assertNotEqual(code, 0)
        self.assertIn("serve.py", msg)
        self.assertIn("LECTOR_DIR", msg)
        self.assertFalse(self.agents.exists())
        self.assertEqual(run.calls, [])

    def test_sinPesosNoEscribeNada(self):
        shutil.rmtree(self.hf)
        code, msg = self.instalar(Fake())
        self.assertNotEqual(code, 0)
        self.assertIn("pesos", msg)
        self.assertFalse(self.agents.exists())

    def test_sinUvNoEscribeNada(self):
        code, msg = self.instalar(Fake(), self.cfg(uv=None))
        self.assertNotEqual(code, 0)
        self.assertIn("uv", msg)
        self.assertFalse(self.agents.exists())

    def test_escribeElPlistYHaceBootstrap(self):
        run = Fake()
        code, msg = self.instalar(run)
        self.assertEqual(code, 0, msg)
        self.assertTrue(self.plist.exists())
        self.assertIn(["launchctl", "bootstrap", "gui/501", str(self.plist)], run.calls)
        self.assertIn("responde", msg)

    def test_paraElPuenteManualAntesDeCargar(self):
        run = Fake(listening="4242\n", command="python serve.py --quant 8")
        code, msg = self.instalar(run)
        self.assertEqual(code, 0, msg)
        kill = run.calls.index(["kill", "4242"])
        self.assertLess(kill, run.calls.index(["launchctl", "bootstrap", "gui/501", str(self.plist)]))
        self.assertIn("4242", msg)

    def test_noParaUnProcesoQueNoEsServePy(self):
        run = Fake(listening="999\n", command="/usr/bin/otra-cosa")
        code, msg = self.instalar(run)
        self.assertNotEqual(code, 0)
        self.assertNotIn(["kill", "999"], run.calls)
        self.assertFalse(self.plist.exists())

    def test_siNoResponderAvisaPeroDejaElAgente(self):
        code, msg = self.instalar(Fake(), health=lambda: False)
        self.assertNotEqual(code, 0)
        self.assertIn(str(self.log), msg)


class QuitarTest(Base):

    def test_hacebootoutYBorra(self):
        self.agents.mkdir()
        self.plist.write_text("x")
        run = Fake()
        code, msg = self.call(al.quitar, self.cfg(), run)
        self.assertEqual(code, 0)
        self.assertIn(["launchctl", "bootout", f"gui/501/{LABEL}"], run.calls)
        self.assertFalse(self.plist.exists())

    def test_sinPlistNoFallaYLoDice(self):
        run = Fake()
        code, msg = self.call(al.quitar, self.cfg(), run)
        self.assertEqual(code, 0)
        self.assertIn("no estaba instalado", msg)
        self.assertEqual(run.calls, [])


class EstadoTest(Base):
    PRINT = f"gui/501/{LABEL} = {{\n\tstate = running\n\tpid = 777\n\tlast exit code = 1\n}}\n"

    def test_cargado(self):
        code, msg = self.call(al.estado, self.cfg(), Fake(print_out=self.PRINT), lambda: True)
        self.assertEqual(code, 0)
        for text in ("running", "777", "1", "responde"):
            self.assertIn(text, msg)

    def test_noCargado(self):
        code, msg = self.call(al.estado, self.cfg(), Fake(print_code=113), lambda: False)
        self.assertIn("no está cargado", msg)
        self.assertIn("no responde", msg)


if __name__ == "__main__":
    unittest.main()
