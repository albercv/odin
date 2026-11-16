import json
import os
import signal
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

import vigia_lector as vl

SCRIPT = Path(__file__).with_name("vigia_lector.py")


class HangWatchTest(unittest.TestCase):

    def test_conRespuestasCorrectasNoSaltaNunca(self):
        watch = vl.HangWatch(100, start=0)
        for t in range(0, 100000, 60):
            watch.healthy(t)
            self.assertFalse(watch.hung(t))

    def test_saltaDesdeElArranqueSiNoHuboRespuesta(self):
        watch = vl.HangWatch(100, start=10)
        self.assertFalse(watch.hung(109))
        self.assertTrue(watch.hung(110))

    def test_saltaDesdeLaUltimaRespuestaCorrecta(self):
        watch = vl.HangWatch(100, start=0)
        watch.healthy(50)
        self.assertFalse(watch.hung(149))
        self.assertTrue(watch.hung(150))

    def test_unaRespuestaCorrectaReiniciaLaCuenta(self):
        watch = vl.HangWatch(100, start=0)
        watch.healthy(90)
        watch.healthy(180)
        self.assertFalse(watch.hung(279))
        self.assertTrue(watch.hung(280))


class Health(BaseHTTPRequestHandler):
    def do_GET(self):
        body = json.dumps({"status": "ok"}).encode()
        self.send_response(200)
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


class ProbeTest(unittest.TestCase):

    def test_sanoSoloConStatusOk(self):
        server = HTTPServer(("127.0.0.1", 0), Health)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        self.assertTrue(vl.probe(f"http://127.0.0.1:{server.server_port}/health", 1))

    def test_puertoCerradoNoEstaSano(self):
        self.assertFalse(vl.probe(f"http://127.0.0.1:{free_port()}/health", 1))


def free_port():
    with HTTPServer(("127.0.0.1", 0), Health) as s:
        return s.server_port


class ProcessTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.pidfile = Path(self.tmp.name) / "hijo.pid"
        self.procs = []

    def tearDown(self):
        for proc in self.procs:
            if proc.poll() is None:
                proc.kill()
                proc.wait()
            proc.stderr.close()
        if self.pidfile.exists():
            self.kill_child()
        self.tmp.cleanup()

    def kill_child(self):
        try:
            os.killpg(int(self.pidfile.read_text()), signal.SIGKILL)
        except (ProcessLookupError, ValueError):
            pass

    def sleeper(self):
        code = f"import os,time;open({str(self.pidfile)!r},'w').write(str(os.getpid()));time.sleep(60)"
        return [sys.executable, "-c", code]

    def spawn(self, url, cmd, hang_after="1", interval="0.2"):
        proc = subprocess.Popen([sys.executable, str(SCRIPT), "--health-url", url, "--hang-after", hang_after,
                                 "--interval", interval, "--"] + cmd, stderr=subprocess.PIPE, text=True)
        self.procs.append(proc)
        return proc

    def child_gone(self):
        try:
            os.kill(int(self.pidfile.read_text()), 0)
        except ProcessLookupError:
            return True
        return False

    def wait_pidfile(self):
        end = time.monotonic() + 5
        while time.monotonic() < end and not (self.pidfile.exists() and self.pidfile.read_text()):
            time.sleep(0.05)
        self.assertTrue(self.pidfile.exists())

    def closed_url(self):
        return f"http://127.0.0.1:{free_port()}/health"

    def test_colgadoSaleConUnoYParaAlHijo(self):
        start = time.monotonic()
        proc = self.spawn(self.closed_url(), self.sleeper())
        self.assertEqual(proc.wait(timeout=10), 1)
        self.assertLess(time.monotonic() - start, 5)
        self.assertTrue(self.child_gone())
        self.assertIn("reinicio el puente", proc.stderr.read())

    def test_siElHijoTerminaSaleConSuCodigo(self):
        proc = self.spawn(self.closed_url(), [sys.executable, "-c", "raise SystemExit(3)"], hang_after="60")
        self.assertEqual(proc.wait(timeout=10), 3)

    def test_sigtermParaAlHijoYSaleConCero(self):
        proc = self.spawn(self.closed_url(), self.sleeper(), hang_after="60")
        self.wait_pidfile()
        proc.send_signal(signal.SIGTERM)
        self.assertEqual(proc.wait(timeout=10), 0)
        self.assertTrue(self.child_gone())

    def test_conElPuenteSanoNoHaceNada(self):
        server = HTTPServer(("127.0.0.1", 0), Health)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        proc = self.spawn(f"http://127.0.0.1:{server.server_port}/health", self.sleeper())
        time.sleep(3)
        self.assertIsNone(proc.poll())
        proc.send_signal(signal.SIGTERM)
        self.assertEqual(proc.wait(timeout=10), 0)
        self.assertTrue(self.child_gone())


if __name__ == "__main__":
    unittest.main()
