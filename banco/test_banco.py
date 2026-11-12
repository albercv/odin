"""Pruebas de la orden de entrada del banco (run, sweep, report). Con claude, juez y Ollama falsos; nunca los reales."""
import contextlib
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from banco import banco
from banco.config import ConfigError
from banco.fakes.fake_ollama import start_fake_ollama
from banco.layout import Layout
from banco.run import draw_sample, prepare

BANCO = Path(__file__).resolve().parent
EXP = BANCO.parent / "experimentos" / "2026-11-12-archivo-ficticio"
FAKE_CLAUDE = str(EXP / "medir" / "fake_claude.py")
FAKE_JUDGE = str(BANCO / "fakes" / "fake_judge.py")
CONFIG = """name = "prueba"
archives = ["ficticio"]
battery = "desarrollo"
mode = "explore"
repetitions = 1
budgetUsd = {budget}
concurrency = 1

[[searchers]]
approach = "main"
count = 2
model = "haiku"

[combiner]
kind = "{kind}"
{combiner_model}
[[judges]]
rubric = "strict"
count = 2
model = "haiku"

[[judges]]
rubric = "strict"
count = 1
model = "ollama:qwen3.5:9b"

[judging]
aggregation = "majority"
{sweep}"""
SWEEP = """
[sweep]
mixes = [{ main = 1 }, { main = 2 }]
draws = 3
singleMaxTurns = [10]
"""


@contextlib.contextmanager
def fake_env(**env):
    """Entorno sin variables FAKE_ heredadas, más las indicadas."""
    base = {k: v for k, v in os.environ.items() if not k.startswith("FAKE_")}
    with mock.patch.dict(os.environ, base, clear=True):
        os.environ.update(env)
        yield


def _real_items(n):
    """Las primeras n preguntas de la batería con su cita resuelta: su documento se mapea a un id falso."""
    items = json.loads((EXP / "datos" / "bateria.json").read_text(encoding="utf-8"))
    return [i for i in items if i["citas_resueltas"] and i.get("claves")][:n]


class Harness(unittest.TestCase):
    """Un directorio temporal con carga.json, batería de tres preguntas y configuración TOML."""

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.tmp = Path(tmp.name)
        self.items = _real_items(3)
        self.estado = self.tmp / "estado"
        self.estado.mkdir()
        doc_ruta = {f"doc-{n}": item["citas_resueltas"][0]["doc"] for n, item in enumerate(self.items, start=1)}
        claves = {item["actor"]: "clave-de-prueba" for item in self.items}
        carga = {"url_mcp": "http://127.0.0.1:1/", "claves": claves, "doc_ruta": doc_ruta, "ficha_f": {}}
        (self.estado / "carga.json").write_text(json.dumps(carga), encoding="utf-8")
        bateria = self.tmp / "bateria.json"
        bateria.write_text(json.dumps(self.items, ensure_ascii=False), encoding="utf-8")
        url, stop = start_fake_ollama("ok")
        self.addCleanup(stop)
        self.layout = Layout(banco=BANCO, exp=EXP, bateria=bateria, estado=self.estado, resultados=self.tmp / "resultados",
                             cache=self.tmp / "cache", repo=BANCO.parent, claude_bin=FAKE_CLAUDE, claude_juez_bin=FAKE_JUDGE,
                             ollama_url=url, timeout=30)

    def config(self, budget=5.0, kind="majorityVote", combiner_model="", sweep=""):
        path = self.tmp / "experimento.toml"
        path.write_text(CONFIG.format(budget=budget, kind=kind, combiner_model=combiner_model, sweep=sweep), encoding="utf-8")
        return path

    def read_jsonl(self, folder, name):
        return [json.loads(x) for x in (folder / name).read_text(encoding="utf-8").splitlines() if x.strip()]

    def only_folder(self):
        folders = list((self.tmp / "resultados").iterdir())
        self.assertEqual(len(folders), 1)
        return folders[0]


class RunTest(Harness):
    def test_end_to_end_with_fakes(self):
        with fake_env(FAKE_VEREDICTO="si"):
            code = banco.main(["run", "--config", str(self.config())], layout=self.layout)
        folder = self.only_folder()
        self.assertEqual(code, 0)
        for name in ("ORIGEN.txt", "ids.json", "buscadores.jsonl", "final.jsonl", "veredictos.jsonl", "resumen.json", "informe.md"):
            self.assertTrue((folder / name).exists(), name)
        resumen = json.loads((folder / "resumen.json").read_text(encoding="utf-8"))
        self.assertEqual(resumen["accuracy"]["total"], 3)
        for key in ("kappa", "falseAcceptRate"):
            self.assertIn(key, resumen["judges"])
        self.assertIn("p95", resumen["latency"])
        self.assertIn("notifiedUsd", resumen["cost"])
        informe = (folder / "informe.md").read_text(encoding="utf-8")
        self.assertIn("Acierto", informe)
        self.assertIn("Jueces", informe)
        self.assertEqual(len(self.read_jsonl(folder, "buscadores.jsonl")), 6)
        self.assertEqual(len(self.read_jsonl(folder, "veredictos.jsonl")), 3)
        self.assertEqual(len(self.read_jsonl(folder, "final.jsonl")), 3)

    def test_report_rebuild_without_ai(self):
        with fake_env(FAKE_VEREDICTO="si"):
            banco.main(["run", "--config", str(self.config())], layout=self.layout)
        folder = self.only_folder()
        antes = json.loads((folder / "resumen.json").read_text(encoding="utf-8"))
        (folder / "resumen.json").unlink()
        (folder / "informe.md").unlink()
        with mock.patch("subprocess.run", side_effect=AssertionError("report no debe lanzar procesos")):
            code = banco.main(["report", "--results", str(folder)], layout=self.layout)
        self.assertEqual(code, 0)
        self.assertEqual(json.loads((folder / "resumen.json").read_text(encoding="utf-8")), antes)
        self.assertIn("Acierto", (folder / "informe.md").read_text(encoding="utf-8"))

    def test_aislamiento_roto_para_y_sale_con_codigo_2(self):
        with fake_env(FAKE_MODO="tools_extra", FAKE_VEREDICTO="si"):
            code = banco.main(["run", "--config", str(self.config())], layout=self.layout)
        folder = self.only_folder()
        self.assertEqual(code, 2)
        self.assertIn("AISLAMIENTO ROTO", (folder / "PARADA.txt").read_text(encoding="utf-8"))
        self.assertIn("Parada", (folder / "informe.md").read_text(encoding="utf-8"))

    def test_tope_de_gasto_escribe_lo_que_haya(self):
        with fake_env(FAKE_VEREDICTO="si"):
            code = banco.main(["run", "--config", str(self.config(budget=0.0015))], layout=self.layout)
        folder = self.only_folder()
        self.assertEqual(code, 2)
        self.assertIn("Tope de gasto", (folder / "PARADA.txt").read_text(encoding="utf-8"))
        self.assertEqual(len(self.read_jsonl(folder, "buscadores.jsonl")), 2)

    def test_composer_compone_la_respuesta_final_con_el_redactor(self):
        with fake_env(FAKE_VEREDICTO="si"):
            path = self.config(kind="composer", combiner_model='model = "haiku"')
            code = banco.main(["run", "--config", str(path)], layout=self.layout)
        self.assertEqual(code, 0)
        finals = self.read_jsonl(self.only_folder(), "final.jsonl")
        self.assertEqual({f["approach"] for f in finals}, {"composer"})


class SweepTest(Harness):
    def test_sweep_escribe_curvas_y_seccion(self):
        code = banco.main(["sweep", "--config", str(self.config(sweep=SWEEP))], layout=self.layout)
        folder = self.only_folder()
        self.assertEqual(code, 0)
        curvas = json.loads((folder / "curvas.json").read_text(encoding="utf-8"))
        self.assertEqual(len(curvas["mezclas"]), 2)
        self.assertEqual(len(curvas["single"]), 1)
        self.assertIn("Curvas", (folder / "informe.md").read_text(encoding="utf-8"))


class SampleAndConfigTest(Harness):
    def test_muestra_estratificada_es_determinista_y_reparte_por_tipo(self):
        items = [{"id": f"Q{n}", "tipo": t} for n, t in enumerate(["a", "a", "a", "b", "b", "c"], start=1)]
        picked = draw_sample(items, "tipo", 3, seed=7)
        self.assertEqual(picked, draw_sample(items, "tipo", 3, seed=7))
        self.assertEqual({i["tipo"] for i in picked}, {"a", "b", "c"})

    def test_capa_no_disponible_es_error_claro(self):
        text = self.config().read_text(encoding="utf-8").replace('archives = ["ficticio"]', 'archives = ["publico"]')
        path = self.tmp / "publico.toml"
        path.write_text(text, encoding="utf-8")
        with self.assertRaisesRegex(ConfigError, "capa no disponible todavía: publico"):
            prepare(path, self.layout)

    def test_reserva_no_existe_en_la_fase_0(self):
        text = self.config().read_text(encoding="utf-8").replace('battery = "desarrollo"', 'battery = "reserva"')
        path = self.tmp / "reserva.toml"
        path.write_text(text.replace('mode = "explore"', 'mode = "closing"'), encoding="utf-8")
        with self.assertRaisesRegex(ConfigError, "reserva"):
            prepare(path, self.layout)


if __name__ == "__main__":
    unittest.main()
