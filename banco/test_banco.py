"""Pruebas de la orden de entrada del banco (run, sweep, report). Con claude, juez y Ollama falsos; nunca los reales."""
import contextlib
import io
import json
import os
import subprocess
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path
from unittest import mock

from types import SimpleNamespace

from banco import banco, judge, run
from banco.cache import RunCache
from banco.ledger import Ledger
from banco.config import ConfigError
from banco.fakes.fake_ollama import start_fake_ollama
from banco.layout import Layout
from banco.medir_path import ejecutar
from banco.run import code_fingerprint, draw_sample, prepare

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
    """Las primeras n preguntas de la batería con cita resuelta y clave: su documento se mapea a un id falso."""
    items = json.loads((EXP / "datos" / "bateria.json").read_text(encoding="utf-8"))
    return [i for i in items if i["citas_resueltas"] and i.get("claves")][:n]


class Harness(unittest.TestCase):
    """Un directorio temporal con carga.json, batería de tres preguntas y configuración TOML."""

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.tmp = Path(tmp.name)
        self.items = _real_items(3)
        self.state_dir = self.tmp / "estado"
        self.state_dir.mkdir()
        doc_ruta = {f"doc-{n}": item["citas_resueltas"][0]["doc"] for n, item in enumerate(self.items, start=1)}
        keys_by_actor = {item["actor"]: "clave-de-prueba" for item in self.items}
        stack = {"url_mcp": "http://127.0.0.1:1/", "claves": keys_by_actor, "doc_ruta": doc_ruta, "ficha_f": {}}
        (self.state_dir / "carga.json").write_text(json.dumps(stack), encoding="utf-8")
        battery_file = self.tmp / "bateria.json"
        battery_file.write_text(json.dumps(self.items, ensure_ascii=False), encoding="utf-8")
        url, stop = start_fake_ollama("ok")
        self.addCleanup(stop)
        self.layout = Layout(banco=BANCO, exp=EXP, battery_file=battery_file, state_dir=self.state_dir,
                             results_dir=self.tmp / "resultados", cache=self.tmp / "cache", repo=BANCO.parent,
                             claude_bin=FAKE_CLAUDE, claude_judge_bin=FAKE_JUDGE, ollama_url=url, timeout=30)
        real_responder = ejecutar.responder

        def citing(item, *args, **kwargs):
            """El claude falso responde «no consta» sin citas; aquí responde con una cita, para que haya material que juzgar."""
            record = real_responder(item, *args, **kwargs)
            if record.get("respuesta") is not None:
                record["respuesta"] = {"respuesta": "Respuesta de prueba.", "no_lo_se": False,
                                       "citas": [{"documentId": "doc-1", "cardId": None, "pagina": 1, "fragmento": "prueba"}]}
            return record

        patcher = mock.patch("banco.medir_path.ejecutar.responder", side_effect=citing)
        patcher.start()
        self.addCleanup(patcher.stop)

    def config(self, budget=5.0, kind="majorityVote", combiner_model="", sweep="", battery="desarrollo", mode="explore",
               concurrency=1):
        path = self.tmp / "experimento.toml"
        text = CONFIG.format(budget=budget, kind=kind, combiner_model=combiner_model, sweep=sweep).replace(
            "concurrency = 1", f"concurrency = {concurrency}")
        text = text.replace('battery = "desarrollo"', f'battery = "{battery}"').replace('mode = "explore"', f'mode = "{mode}"')
        path.write_text(text, encoding="utf-8")
        return path

    def read_jsonl(self, folder, name):
        return [json.loads(x) for x in (folder / name).read_text(encoding="utf-8").splitlines() if x.strip()]

    def only_folder(self):
        folders = list((self.tmp / "resultados").iterdir())
        self.assertEqual(len(folders), 1)
        return folders[0]

    def run_cli(self, config, env=None, preflight=False):
        flags = [] if preflight else ["--sin-prueba"]
        with fake_env(**{"FAKE_VEREDICTO": "si", **(env or {})}):
            return banco.main(["run", *flags, "--config", str(config)], layout=self.layout)


class RunTest(Harness):
    def test_end_to_end_with_fakes(self):
        code = self.run_cli(self.config())
        folder = self.only_folder()
        self.assertEqual(code, 0)
        for name in ("ORIGEN.txt", "ids.json", "buscadores.jsonl", "final.jsonl", "veredictos.jsonl", "resumen.json", "informe.md"):
            self.assertTrue((folder / name).exists(), name)
        summary = json.loads((folder / "resumen.json").read_text(encoding="utf-8"))
        self.assertEqual(summary["accuracy"]["total"], 3)
        for key in ("kappa", "falseAcceptRate"):
            self.assertIn(key, summary["judges"])
        self.assertIn("p95", summary["latency"])
        self.assertIn("notifiedUsd", summary["cost"])
        report = (folder / "informe.md").read_text(encoding="utf-8")
        self.assertIn("Acierto", report)
        self.assertIn("Jueces", report)
        self.assertEqual(len(self.read_jsonl(folder, "buscadores.jsonl")), 6)
        self.assertEqual(len(self.read_jsonl(folder, "veredictos.jsonl")), 3)
        self.assertEqual(len(self.read_jsonl(folder, "final.jsonl")), 3)

    def test_report_rebuild_without_ai(self):
        self.run_cli(self.config())
        folder = self.only_folder()
        before = json.loads((folder / "resumen.json").read_text(encoding="utf-8"))
        (folder / "resumen.json").unlink()
        (folder / "informe.md").unlink()
        with mock.patch("subprocess.run", side_effect=AssertionError("report no debe lanzar procesos")):
            code = banco.main(["report", "--results", str(folder)], layout=self.layout)
        self.assertEqual(code, 0)
        self.assertEqual(json.loads((folder / "resumen.json").read_text(encoding="utf-8")), before)
        self.assertIn("Acierto", (folder / "informe.md").read_text(encoding="utf-8"))

    def test_broken_isolation_stops_with_code_2(self):
        code = self.run_cli(self.config(), env={"FAKE_MODO": "tools_extra"})
        folder = self.only_folder()
        self.assertEqual(code, 2)
        self.assertIn("AISLAMIENTO ROTO", (folder / "PARADA.txt").read_text(encoding="utf-8"))
        self.assertIn("Parada", (folder / "informe.md").read_text(encoding="utf-8"))

    def test_budget_cap_writes_partial_results(self):
        code = self.run_cli(self.config(budget=0.0015, concurrency=1))
        folder = self.only_folder()
        self.assertEqual(code, 2)
        self.assertIn("Tope de gasto", (folder / "PARADA.txt").read_text(encoding="utf-8"))
        self.assertEqual(len(self.read_jsonl(folder, "buscadores.jsonl")), 2)
        ledger = json.loads((folder / "libro.json").read_text(encoding="utf-8"))
        summary = json.loads((folder / "resumen.json").read_text(encoding="utf-8"))
        self.assertAlmostEqual(ledger["spent"], 0.002)
        self.assertEqual(summary["cost"]["notifiedUsd"], 0.002)

    def test_composer_composes_final_answer(self):
        code = self.run_cli(self.config(kind="composer", combiner_model='model = "haiku"'))
        self.assertEqual(code, 0)
        finals = self.read_jsonl(self.only_folder(), "final.jsonl")
        self.assertEqual({f["approach"] for f in finals}, {"composer"})

    def test_composer_spend_crossing_cap_stops_and_writes_stop_file(self):
        # 6 buscadores × 0,001 USD = 0,006; el primer redactor cruza el tope de 0,0065 USD
        code = self.run_cli(self.config(budget=0.0065, kind="composer", combiner_model='model = "haiku"', concurrency=1))
        folder = self.only_folder()
        self.assertEqual(code, 2)
        self.assertIn("Tope de gasto", (folder / "PARADA.txt").read_text(encoding="utf-8"))
        finals = self.read_jsonl(folder, "final.jsonl")
        self.assertEqual(len(finals), 3)
        self.assertEqual(sum(f["answer"] is not None for f in finals), 1)
        verdicts = self.read_jsonl(folder, "veredictos.jsonl")  # parada antes de jueces: sin jueces, la pregunta queda sin veredicto
        self.assertEqual([(v["jury"], v["judges"], v["motivo"]) for v in verdicts], [(None, [], "parada")])
        summary = json.loads((folder / "resumen.json").read_text(encoding="utf-8"))
        self.assertEqual(summary["accuracy"]["total"], 1)  # las dos sin respuesta no son fallos
        self.assertEqual(summary["notMeasured"], 2)
        self.assertIn("No medidas (parada o error): 2", (folder / "informe.md").read_text(encoding="utf-8"))

    def test_judge_spend_crossing_cap_stops_and_writes_stop_file(self):
        # buscadores 0,006 USD; los dos jueces de Haiku de la primera pregunta llegan a 0,007 USD (tope 0,0068)
        code = self.run_cli(self.config(budget=0.0068, concurrency=1))
        folder = self.only_folder()
        self.assertEqual(code, 2)
        self.assertIn("Tope de gasto", (folder / "PARADA.txt").read_text(encoding="utf-8"))
        verdicts = self.read_jsonl(folder, "veredictos.jsonl")
        # sin presupuesto, los jueces de Claude no corren, pero el de Ollama (gratis) sí
        self.assertEqual([len(v["judges"]) for v in verdicts], [3, 1, 1])
        self.assertIsNone(verdicts[0]["jury"])  # el segundo juez de pago agota el tope: la pregunta queda sin veredicto
        self.assertEqual(verdicts[0]["motivo"], "parada")
        self.assertIsNone(verdicts[1]["jury"])

    def test_quota_on_last_paid_judge_leaves_question_without_verdict(self):
        real_judge = judge.run_claude_judge
        calls = []

        def second_hits_quota(*args, **kwargs):
            calls.append(1)
            if len(calls) == 2:  # el último juez de pago de la primera pregunta
                return {"model": "haiku", "verdict": {"invalido": True, "motivo": "sin veredicto"}, "coste_usd": 0.0005,
                        "tokens": None, "duracion_s": 1.0, "error": "Claude AI usage limit reached"}
            return real_judge(*args, **kwargs)

        with mock.patch("banco.judge.run_claude_judge", side_effect=second_hits_quota):
            code = self.run_cli(self.config(concurrency=1))
        folder = self.only_folder()
        self.assertEqual(code, 2)
        self.assertIn("cuota o límite en el juez de Q001", (folder / "PARADA.txt").read_text(encoding="utf-8"))
        first = self.read_jsonl(folder, "veredictos.jsonl")[0]
        self.assertIsNone(first["jury"])  # hay veredictos parciales, pero la parada alcanza a la pregunta
        self.assertEqual(first["motivo"], "parada")
        self.assertEqual([j["verdict"].get("correcta") for j in first["judges"]][:1], [True])  # el primero terminó
        self.assertIn("usage limit", first["judges"][1]["error"])
        self.assertEqual(len(first["judges"]), 3)  # el de Ollama, gratis, también corrió

    def test_judge_quota_error_is_stop_reason(self):
        code = self.run_cli(self.config(), env={"FAKE_VEREDICTO": "limite"})
        folder = self.only_folder()
        self.assertEqual(code, 2)
        self.assertIn("cuota o límite en el juez", (folder / "PARADA.txt").read_text(encoding="utf-8"))
        verdicts = self.read_jsonl(folder, "veredictos.jsonl")
        self.assertIn("usage limit", verdicts[0]["judges"][0]["error"])
        self.assertTrue((folder / "resumen.json").exists())

    def test_sweep_cap_is_cumulative_across_pools(self):
        # el grupo principal (0,006 USD) no pasa de 0,0075; los turnos suman hasta cruzarlo
        code = banco.main(["sweep", "--sin-prueba", "--config", str(self.config(budget=0.0075, sweep=SWEEP))], layout=self.layout)
        folder = self.only_folder()
        self.assertEqual(code, 2)
        self.assertIn("Tope de gasto", (folder / "PARADA.txt").read_text(encoding="utf-8"))
        self.assertFalse((folder / "curvas.json").exists())
        self.assertEqual(len(self.read_jsonl(folder, "turnos.jsonl")), 2)

    def test_ollama_judge_gets_configured_timeout_and_think(self):
        path = self.config()
        path.write_text(path.read_text(encoding="utf-8").replace(
            'model = "ollama:qwen3.5:9b"', 'model = "ollama:qwen3.5:9b"\ntimeoutS = 7'), encoding="utf-8")
        with mock.patch("banco.judge.run_ollama_judge", side_effect=judge.run_ollama_judge) as ollama:
            self.run_cli(path)
        self.assertEqual(ollama.call_args.args[4], 7)
        self.assertIs(ollama.call_args.kwargs["think"], False)

    def test_no_consta_answers_are_not_judged(self):
        real = ejecutar.responder

        def no_consta(item, *args, **kwargs):
            record = real(item, *args, **kwargs)
            record["respuesta"] = {"respuesta": "No consta.", "no_lo_se": True, "citas": []}
            return record

        with mock.patch("banco.medir_path.ejecutar.responder", side_effect=no_consta), \
                mock.patch("banco.judge.run_claude_judge") as claude, mock.patch("banco.judge.run_ollama_judge") as ollama:
            code = self.run_cli(self.config())
        folder = self.only_folder()
        self.assertEqual(code, 0)
        claude.assert_not_called()
        ollama.assert_not_called()
        verdicts = self.read_jsonl(folder, "veredictos.jsonl")
        self.assertEqual([v["motivo"] for v in verdicts], ["no juzgable: no consta"] * 3)
        self.assertTrue(all(v["jury"] is None and v["judges"] == [] for v in verdicts))
        summary = json.loads((folder / "resumen.json").read_text(encoding="utf-8"))
        self.assertEqual(summary["judges"]["notJudgedNoConsta"], 3)
        self.assertEqual(summary["judges"]["manualReview"], [])  # las tres tienen clave: decide el script
        self.assertIn("«No consta» sin juzgar: 3", (folder / "informe.md").read_text(encoding="utf-8"))

    def test_judge_exception_keeps_other_results(self):
        real_judge = judge.run_claude_judge
        calls = []

        def flaky(*args, **kwargs):
            calls.append(1)
            if len(calls) == 1:
                raise RuntimeError("fallo del juez")
            return real_judge(*args, **kwargs)

        with mock.patch("banco.judge.run_claude_judge", side_effect=flaky):
            code = self.run_cli(self.config(concurrency=1))
        folder = self.only_folder()
        # un juez de pago que falla deja su coste desconocido: el presupuesto se agota y para
        self.assertEqual(code, 2)
        self.assertIn("coste desconocido", (folder / "PARADA.txt").read_text(encoding="utf-8"))
        verdicts = self.read_jsonl(folder, "veredictos.jsonl")
        self.assertEqual(len(verdicts), 3)
        self.assertIn("fallo del juez", verdicts[0]["error"])
        self.assertIsNone(verdicts[0]["jury"])
        self.assertEqual([len(v["judges"]) for v in verdicts], [0, 1, 1])  # solo los gratis de Ollama
        self.assertTrue((folder / "resumen.json").exists())

    def test_composer_exception_keeps_other_results(self):
        real_compose = run.compose
        calls = []

        def flaky(*args, **kwargs):
            calls.append(1)
            if len(calls) == 1:
                raise ValueError("redactor roto")
            return real_compose(*args, **kwargs)

        with mock.patch("banco.run.compose", side_effect=flaky):
            code = self.run_cli(self.config(kind="composer", combiner_model='model = "haiku"', concurrency=1))
        folder = self.only_folder()
        self.assertEqual(code, 2)  # coste desconocido en el redactor: el presupuesto se agota
        self.assertIn("coste desconocido", (folder / "PARADA.txt").read_text(encoding="utf-8"))
        finals = self.read_jsonl(folder, "final.jsonl")
        self.assertEqual(len(finals), 3)
        self.assertIn("redactor roto", finals[0]["error"])
        self.assertIsNone(finals[0]["answer"])
        self.assertEqual(finals[1]["error"], "presupuesto agotado")
        self.assertTrue((folder / "resumen.json").exists())

    def test_composer_quota_error_is_stop_reason(self):
        limit = {"respuesta": None, "error": "Claude AI usage limit reached", "coste_usd": 0.0, "tokens": None,
                 "duracion_s": 0.0}
        with mock.patch("banco.run.compose", return_value=limit):
            code = self.run_cli(self.config(kind="composer", combiner_model='model = "haiku"'))
        folder = self.only_folder()
        self.assertEqual(code, 2)
        self.assertIn("cuota o límite", (folder / "PARADA.txt").read_text(encoding="utf-8"))

    def test_unknown_cost_exhausts_budget_and_is_reported(self):
        real = ejecutar.responder
        calls = []

        def without_cost(*args, **kwargs):
            record = real(*args, **kwargs)
            calls.append(1)
            if len(calls) == 1:
                record["coste_usd"] = None  # registro de pago sin coste numérico
            return record

        with mock.patch("banco.medir_path.ejecutar.responder", side_effect=without_cost):
            code = self.run_cli(self.config(concurrency=1))
        folder = self.only_folder()
        self.assertEqual(code, 2)  # una búsqueda nueva sin coste para el experimento
        self.assertLess(len(self.read_jsonl(folder, "buscadores.jsonl")), 6)
        self.assertIn("coste desconocido", (folder / "PARADA.txt").read_text(encoding="utf-8"))
        summary = json.loads((folder / "resumen.json").read_text(encoding="utf-8"))
        self.assertGreaterEqual(summary["cost"]["unknownCostRecords"], 1)
        self.assertIn("coste desconocido", (folder / "informe.md").read_text(encoding="utf-8"))


class PaidPathsTest(Harness):
    """Un fallo tras una llamada de pago agota el presupuesto: nunca se cuenta como coste 0."""

    def _pool_with(self, side_effect_error):
        from banco.cache import RunCache
        from banco.config import SearcherSpec as Spec
        from banco.ledger import Ledger
        from banco.pool import PoolSettings, run_pool
        real = ejecutar.responder
        calls = []

        def failing_first(*args, **kwargs):
            calls.append(1)
            if len(calls) == 1:
                raise side_effect_error
            return real(*args, **kwargs)

        ledger = Ledger(100.0)
        settings = PoolSettings(claude_bin=FAKE_CLAUDE, timeout=30, concurrency=1, budget_usd=100.0, max_turns=None,
                                odin_commit="abc")
        stop = self.tmp / "PARADA.txt"
        with mock.patch("banco.medir_path.ejecutar.responder", side_effect=failing_first):
            with self.assertRaises(type(side_effect_error)):
                run_pool(self.items, (Spec("main", 2, "haiku"),), {"url_mcp": "x", "claves": {}, "doc_ruta": {}, "ficha_f": {}},
                         RunCache(self.tmp / "cache2"), settings, BANCO / "enfoques", stop, ledger=ledger)
        return ledger

    def test_searcher_aislamiento_charges_unknown_cost(self):
        ledger = self._pool_with(ejecutar.Aislamiento("juez no aislado"))
        self.assertEqual(ledger.unknown, 1)
        self.assertFalse(ledger.can_launch())

    def test_searcher_unexpected_error_charges_unknown_cost(self):
        ledger = self._pool_with(RuntimeError("fallo inesperado"))
        self.assertEqual(ledger.unknown, 1)
        self.assertFalse(ledger.can_launch())

    def test_unexpected_pool_error_still_writes_cached_records(self):
        real = ejecutar.responder
        calls = []

        def second_fails(*args, **kwargs):
            calls.append(1)
            if len(calls) == 2:
                raise RuntimeError("fallo inesperado")
            return real(*args, **kwargs)

        with mock.patch("banco.medir_path.ejecutar.responder", side_effect=second_fails):
            with self.assertRaises(RuntimeError):
                self.run_cli(self.config(concurrency=1))
        folder = self.only_folder()
        self.assertEqual(len(self.read_jsonl(folder, "buscadores.jsonl")), 1)
        self.assertTrue((folder / "resumen.json").exists())
        self.assertAlmostEqual(json.loads((folder / "libro.json").read_text(encoding="utf-8"))["spent"], 0.001)


class BudgetPersistenceTest(Harness):
    """El tope es del experimento: relanzar tras una parada no vuelve a gastar; la opción reinicia el libro."""

    def test_relaunch_after_budget_stop_does_not_spend(self):
        self.assertEqual(self.run_cli(self.config(budget=0.0015)), 2)
        with mock.patch("banco.medir_path.ejecutar.responder", wraps=ejecutar.responder) as responder, contextlib.redirect_stderr(io.StringIO()) as errors:
            code = banco.main(["run", "--sin-prueba", "--config", str(self.config(budget=0.0015))], layout=self.layout)
        self.assertEqual(code, 2)
        self.assertEqual(responder.call_count, 0)
        self.assertIn("ya gastó", errors.getvalue())
        self.assertEqual(len(list((self.tmp / "resultados").iterdir())), 1)  # no se crea carpeta nueva

    def test_reiniciar_presupuesto_starts_the_book_again(self):
        self.assertEqual(self.run_cli(self.config(budget=0.0015)), 2)
        with fake_env(FAKE_VEREDICTO="si"):
            code = banco.main(["run", "--sin-prueba", "--config", str(self.config(budget=0.0015)), "--reiniciar-presupuesto"],
                              layout=self.layout)
        folders = list((self.tmp / "resultados").iterdir())
        self.assertEqual(len(folders), 2)
        self.assertEqual(code, 2)  # el tope de 0,0015 USD vuelve a cortar la nueva orden, pero ya no se niega al arrancar
        records = self.read_jsonl(max(folders), "buscadores.jsonl")
        self.assertEqual(sum(not r["cached"] for r in records), 2)  # las dos de la primera orden salen de la caché
        self.assertIn("Tope de gasto", (max(folders) / "PARADA.txt").read_text(encoding="utf-8"))
        self.assertEqual(len(list((self.state_dir).glob("libro-prueba.*.json"))), 1)


class RelaunchCacheTest(Harness):
    """Un relanzamiento con el libro reiniciado no vuelve a pagar redactor ni jueces: sale de la caché, a coste 0."""

    def test_relaunch_with_reset_book_does_not_call_composer_or_judges(self):
        composer = self.config(kind="composer", combiner_model='model = "haiku"')
        self.assertEqual(self.run_cli(composer), 0)
        with mock.patch("banco.run.compose", wraps=run.compose) as compose_mock, \
                mock.patch("banco.judge.run_claude_judge", wraps=judge.run_claude_judge) as claude, \
                mock.patch("banco.judge.run_ollama_judge", wraps=judge.run_ollama_judge) as ollama, \
                mock.patch("banco.medir_path.ejecutar.responder", wraps=ejecutar.responder) as responder:
            with fake_env(FAKE_VEREDICTO="si"):
                code = banco.main(["run", "--sin-prueba", "--config", str(composer), "--reiniciar-presupuesto"], layout=self.layout)
        self.assertEqual(code, 0)
        self.assertEqual((compose_mock.call_count, claude.call_count, ollama.call_count, responder.call_count), (0, 0, 0, 0))
        folder = max((self.tmp / "resultados").iterdir())
        finals = self.read_jsonl(folder, "final.jsonl")
        self.assertTrue(all(f["cached"] and f["coste_usd"] == 0.0 for f in finals))
        verdicts = self.read_jsonl(folder, "veredictos.jsonl")
        self.assertTrue(all(j["cached"] and j["coste_usd"] == 0.0 for v in verdicts for j in v["judges"]))
        summary = json.loads((folder / "resumen.json").read_text(encoding="utf-8"))
        self.assertEqual(summary["cost"]["notifiedUsd"], 0.0)
        self.assertEqual(summary["accuracy"]["total"], 3)


    def test_changing_think_rejudges_ollama_verdicts_only(self):
        self.assertEqual(self.run_cli(self.config()), 0)
        path = self.config()
        path.write_text(path.read_text(encoding="utf-8").replace(
            'model = "ollama:qwen3.5:9b"', 'model = "ollama:qwen3.5:9b"\nthink = true'), encoding="utf-8")
        with mock.patch("banco.judge.run_claude_judge", wraps=judge.run_claude_judge) as claude, \
                mock.patch("banco.judge.run_ollama_judge", wraps=judge.run_ollama_judge) as ollama:
            with fake_env(FAKE_VEREDICTO="si"):
                code = banco.main(["run", "--sin-prueba", "--config", str(path), "--reiniciar-presupuesto"], layout=self.layout)
        self.assertEqual(code, 0)
        self.assertEqual(claude.call_count, 0)  # mismo juez de Claude: sale de la caché
        self.assertEqual(ollama.call_count, 3)  # think cambió: el veredicto de Ollama no se reutiliza
        self.assertTrue(all(c.kwargs["think"] is True for c in ollama.call_args_list))

    def test_invalid_verdicts_are_not_cached_and_are_judged_again(self):
        self.assertEqual(self.run_cli(self.config(), env={"FAKE_VEREDICTO": "invalido"}), 0)
        with mock.patch("banco.judge.run_claude_judge", wraps=judge.run_claude_judge) as claude:
            with fake_env(FAKE_VEREDICTO="si"):
                code = banco.main(["run", "--sin-prueba", "--config", str(self.config()), "--reiniciar-presupuesto"], layout=self.layout)
        self.assertEqual(code, 0)
        self.assertEqual(claude.call_count, 6)  # 3 preguntas × 2 jueces de Claude: los inválidos no se guardaron

    def test_cached_verdicts_are_served_when_budget_is_exhausted(self):
        self.assertEqual(self.run_cli(self.config()), 0)
        with mock.patch("banco.ledger.Ledger.can_launch", return_value=False), \
                mock.patch("banco.judge.run_claude_judge") as claude, mock.patch("banco.judge.run_ollama_judge") as ollama:
            with fake_env(FAKE_VEREDICTO="si"):
                code = banco.main(["run", "--sin-prueba", "--config", str(self.config()), "--reiniciar-presupuesto"], layout=self.layout)
        self.assertEqual(code, 0)
        self.assertEqual((claude.call_count, ollama.call_count), (0, 0))
        verdicts = self.read_jsonl(max((self.tmp / "resultados").iterdir()), "veredictos.jsonl")
        self.assertEqual(len(verdicts), 3)
        self.assertTrue(all(v["jury"] is not None and v["motivo"] is None for v in verdicts))

    def test_cached_verdicts_are_served_when_the_run_is_stopping(self):
        self.assertEqual(self.run_cli(self.config()), 0)
        real_answer_stage = run.answer_stage

        def stopping(sess, runs, finals):
            real_answer_stage(sess, runs, finals)
            run._stop(sess, "parada de prueba")

        with mock.patch("banco.run.answer_stage", side_effect=stopping), \
                mock.patch("banco.judge.run_claude_judge") as claude:
            with fake_env(FAKE_VEREDICTO="si"):
                code = banco.main(["run", "--sin-prueba", "--config", str(self.config()), "--reiniciar-presupuesto"], layout=self.layout)
        self.assertEqual(code, 2)
        self.assertEqual(claude.call_count, 0)
        verdicts = self.read_jsonl(max((self.tmp / "resultados").iterdir()), "veredictos.jsonl")
        self.assertEqual(len(verdicts), 3)  # la parada no quita los veredictos de la caché
        self.assertTrue(all(v["jury"] is not None for v in verdicts))


class ComposedKeyTest(unittest.TestCase):
    """Un registro de buscador sin clave no hace caer al redactor: compone sin caché segura, sin guardar."""

    def test_run_without_key_composes_without_raising_and_without_caching(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        sess = SimpleNamespace(cfg=SimpleNamespace(combiner=SimpleNamespace(kind="composer", model="haiku")),
                               settings=SimpleNamespace(odin_commit="abc", stack_digest="s"), stack={},
                               cache=RunCache(Path(tmp.name)), ledger=Ledger(10.0))
        item = {"id": "Q1", "pregunta": "¿Cuánto?"}
        runs = [{"id": "Q1", "approach": "main", "replica": 0, "error": None,
                 "respuesta": {"respuesta": "10 €", "no_lo_se": False, "citas": []}}]
        record = {"respuesta": {"respuesta": "10 €", "no_lo_se": False, "citas": []}, "coste_usd": 0.001,
                  "tokens": None, "duracion_s": 1.0, "error": None}
        with mock.patch("banco.run.compose", return_value=record) as compose_mock:
            out = run._composed(sess, item, runs)
            again = run._composed(sess, item, runs)
        self.assertEqual(compose_mock.call_count, 2)  # nada guardado: la segunda también paga
        self.assertFalse(out["cached"])
        self.assertAlmostEqual(sess.ledger.spent, 0.002)


class StopRecordsTest(Harness):
    """Lo pagado antes de una parada, también lo fallido, queda en buscadores.jsonl; lo de la caché no se duplica."""

    def test_quota_stop_keeps_the_failed_paid_record(self):
        code = self.run_cli(self.config(concurrency=1), env={"FAKE_MODO": "limite"})
        folder = self.only_folder()
        self.assertEqual(code, 2)
        records = self.read_jsonl(folder, "buscadores.jsonl")
        self.assertEqual(len(records), 1)
        self.assertIn("usage limit", records[0]["error"])

    def test_stop_keeps_cached_and_fresh_records_once(self):
        real = ejecutar.responder
        calls = []

        def third_hits_quota(item, *args, **kwargs):
            calls.append(1)
            if len(calls) == 3:
                return {"id": item["id"], "actor": item["actor"], "error": "Claude AI usage limit reached",
                        "coste_usd": 0.0, "duracion_s": 1.0, "respuesta": None, "consultas": [], "tokens": None}
            return real(item, *args, **kwargs)

        with mock.patch("banco.medir_path.ejecutar.responder", side_effect=third_hits_quota):
            code = self.run_cli(self.config(concurrency=1))
        folder = self.only_folder()
        self.assertEqual(code, 2)
        records = self.read_jsonl(folder, "buscadores.jsonl")
        keys = [(r["id"], r["approach"], r["replica"]) for r in records]
        self.assertEqual(len(records), 3)
        self.assertEqual(len(set(keys)), 3)


class CodeFingerprintTest(unittest.TestCase):
    """La huella del código: HEAD, y un hash de los cambios sin commit en banco y medir."""

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.repo = Path(tmp.name)
        (self.repo / "banco").mkdir()
        (self.repo / "banco" / "x.py").write_text("a = 1\n", encoding="utf-8")
        (self.repo / "otro.txt").write_text("uno\n", encoding="utf-8")
        self.git("init", "-q")
        self.git("add", "-A")
        self.git("-c", "user.name=prueba", "-c", "user.email=prueba@invalid", "commit", "-qm", "inicio")

    def git(self, *args):
        return subprocess.run(["git", *args], cwd=self.repo, check=True, capture_output=True, text=True)

    def test_clean_tree_is_plain_head(self):
        head, _ = code_fingerprint(self.repo)
        self.assertRegex(head, r"^[0-9a-f]{40}$")

    def test_uncommitted_change_in_bench_code_changes_the_fingerprint(self):
        clean, _ = code_fingerprint(self.repo)
        (self.repo / "banco" / "x.py").write_text("a = 2\n", encoding="utf-8")
        dirty, _ = code_fingerprint(self.repo)
        self.assertNotEqual(clean, dirty)
        self.assertTrue(dirty.startswith(clean + "+"))

    def test_change_outside_the_code_paths_is_ignored(self):
        clean, _ = code_fingerprint(self.repo)
        (self.repo / "otro.txt").write_text("dos\n", encoding="utf-8")
        self.assertEqual(clean, code_fingerprint(self.repo)[0])


class SweepTest(Harness):
    def test_sweep_writes_curves(self):
        code = banco.main(["sweep", "--sin-prueba", "--config", str(self.config(sweep=SWEEP))], layout=self.layout)
        folder = self.only_folder()
        self.assertEqual(code, 0)
        curves = json.loads((folder / "curvas.json").read_text(encoding="utf-8"))
        self.assertEqual(len(curves["mezclas"]), 2)
        self.assertEqual(len(curves["single"]), 1)
        self.assertIn("Curvas", (folder / "informe.md").read_text(encoding="utf-8"))


class SampleAndConfigTest(Harness):
    def test_stratified_sample_is_deterministic(self):
        items = [{"id": f"Q{n}", "tipo": t} for n, t in enumerate(["a", "a", "a", "b", "b", "c"], start=1)]
        picked = draw_sample(items, "tipo", 3, seed=7)
        self.assertEqual(picked, draw_sample(items, "tipo", 3, seed=7))
        self.assertEqual({i["tipo"] for i in picked}, {"a", "b", "c"})

    def test_unsupported_archive_is_clear_error(self):
        text = self.config().read_text(encoding="utf-8").replace('archives = ["ficticio"]', 'archives = ["publico"]')
        path = self.tmp / "publico.toml"
        path.write_text(text, encoding="utf-8")
        with self.assertRaisesRegex(ConfigError, "capa no disponible todavía: publico"):
            prepare(path, self.layout)

    def test_reserve_battery_not_available_in_phase_0(self):
        with self.assertRaisesRegex(ConfigError, "reserva"):
            prepare(self.config(battery="reserva", mode="closing"), self.layout)

    def test_reserve_battery_with_explore_is_rejected_by_cli(self):
        errors = io.StringIO()
        with contextlib.redirect_stderr(errors):
            code = banco.main(["run", "--sin-prueba", "--config", str(self.config(battery="reserva", mode="explore"))], layout=self.layout)
        self.assertEqual(code, 1)
        self.assertIn("reserva", errors.getvalue())



TURNO = str(BANCO / "fakes" / "fake_claude_turno.py")


class ErrorStopTest(Harness):
    """Tres errores seguidos de claude de pago paran el experimento (código 2, PARADA.txt, sin más lanzamientos)."""

    def test_three_claude_errors_in_a_row_stop_the_run(self):
        counter = self.tmp / "contador"
        self.layout = replace(self.layout, claude_bin=TURNO)
        code = self.run_cli(self.config(concurrency=1), env={"FAKE_CONTADOR": str(counter), "FAKE_MODO_DESDE": "1",
                                                            "FAKE_MODO_DESDE_MODO": "error"})
        folder = self.only_folder()
        self.assertEqual(code, 2)
        self.assertEqual(len(counter.read_text(encoding="utf-8").splitlines()), 3)
        self.assertIn("3 errores seguidos de claude: fallo cualquiera", (folder / "PARADA.txt").read_text(encoding="utf-8"))

    def test_three_composer_errors_in_a_row_stop_the_run(self):
        failed = {"respuesta": None, "error": "fallo del redactor", "coste_usd": 0.0, "tokens": None, "duracion_s": 0.0}
        with mock.patch("banco.run.compose", return_value=failed):
            code = self.run_cli(self.config(kind="composer", combiner_model='model = "haiku"'))
        self.assertEqual(code, 2)
        self.assertIn("3 errores seguidos de claude: fallo del redactor",
                      (self.only_folder() / "PARADA.txt").read_text(encoding="utf-8"))

    def test_three_judge_errors_in_a_row_stop_the_run(self):
        failed = {"model": "haiku", "verdict": {"invalido": True, "motivo": "sin veredicto"}, "coste_usd": 0.0005,
                  "tokens": None, "duracion_s": 1.0, "error": "fallo del juez"}
        with mock.patch("banco.judge.run_claude_judge", return_value=failed):
            code = self.run_cli(self.config(concurrency=1))
        self.assertEqual(code, 2)
        self.assertIn("3 errores seguidos de claude: fallo del juez",
                      (self.only_folder() / "PARADA.txt").read_text(encoding="utf-8"))


class PreflightTest(Harness):
    """Llamada de prueba antes del primer grupo: si falla, no se lanza nada; con --sin-prueba no se hace."""

    def setUp(self):
        super().setUp()
        self.layout = replace(self.layout, claude_bin=TURNO)
        self.counter = self.tmp / "contador"

    def _env(self, **extra):
        return {"FAKE_CONTADOR": str(self.counter), "FAKE_MODO_DESDE": "1", **extra}

    def _calls(self):
        return len(self.counter.read_text(encoding="utf-8").splitlines())

    def test_command_is_isolated_and_has_no_odin_tools(self):
        from banco.preflight import build_command
        command = build_command("claude", "haiku", "/tmp/vacio.json")
        self.assertIn("--strict-mcp-config", command)
        self.assertNotIn("--allowedTools", command)
        self.assertNotIn("mcp__odin", " ".join(command))

    def test_ok_preflight_lets_the_group_run(self):
        code = self.run_cli(self.config(concurrency=1), preflight=True, env=self._env(FAKE_MODO_DESDE="99"))
        self.assertEqual(code, 0)
        self.assertEqual(self._calls(), 1 + 6)  # prueba + 6 buscadores
        self.assertEqual(len(self.read_jsonl(self.only_folder(), "buscadores.jsonl")), 6)

    def test_failed_preflight_stops_before_the_group(self):
        code = self.run_cli(self.config(), preflight=True, env=self._env(FAKE_MODO_DESDE_MODO="error"))
        folder = self.only_folder()
        self.assertEqual(code, 2)
        self.assertEqual(self._calls(), 1)  # solo la prueba
        self.assertIn("la llamada de prueba falló: fallo cualquiera", (folder / "PARADA.txt").read_text(encoding="utf-8"))
        self.assertEqual(self.read_jsonl(folder, "buscadores.jsonl"), [])

    def test_limit_in_preflight_stops_too(self):
        code = self.run_cli(self.config(), preflight=True, env=self._env(FAKE_MODO_DESDE_MODO="limite"))
        self.assertEqual(code, 2)
        self.assertIn("la llamada de prueba falló: Claude AI usage limit reached",
                      (self.only_folder() / "PARADA.txt").read_text(encoding="utf-8"))

    def test_sin_prueba_skips_the_preflight(self):
        code = self.run_cli(self.config(concurrency=1), env=self._env(FAKE_MODO_DESDE_MODO="error"))
        self.assertEqual(code, 2)  # el grupo sí se lanza y para por errores seguidos, no por la prueba
        self.assertEqual(self._calls(), 3)
        self.assertNotIn("la llamada de prueba", (self.only_folder() / "PARADA.txt").read_text(encoding="utf-8"))

    def test_failed_preflight_in_sweep_writes_no_curves(self):
        with fake_env(**self._env(FAKE_MODO_DESDE_MODO="error")):
            code = banco.main(["sweep", "--config", str(self.config(sweep=SWEEP))], layout=self.layout)
        folder = self.only_folder()
        self.assertEqual(code, 2)
        self.assertFalse((folder / "curvas.json").exists())
        self.assertIn("la llamada de prueba falló", (folder / "PARADA.txt").read_text(encoding="utf-8"))


class CurveReportTest(unittest.TestCase):
    """El informe de curvas marca las mezclas no válidas por errores y no les da veredicto de «supera single»."""

    def test_invalid_mix_and_turn_rows_say_so(self):
        from banco.report import render
        row = {"mix": {"main": 2}, "n": 2, "accuracy_mean": 0.9, "accuracy_min": 0.8, "accuracy_max": 1.0,
               "tokens_mean": 100.0, "cost_mean": 0.01, "latency_p50": 1.0, "latency_p95": 2.0, "valid": False,
               "invalidDraws": 9, "measuredPairs": 11, "supera_single": None}
        turn = {**row, "mix": {"main": 1}, "max_turns": 10, "valid": False, "accuracy_min": None}
        curves = {"variacion": 0.05, "mezclas": [row], "single": [turn], "single_por_defecto": turn}
        summary = {"origen": {}, "cost": {"notifiedUsd": 0.0, "notifiedUsdFromRecords": 0.0, "unknownCostRecords": 0,
                                          "strategyUsdPerAnswer": None,
                                          "tokensPerAnswer": None, "searchers": {"tokensPerAnswer": None},
                                          "combiner": {"tokensPerAnswer": None}, "judges": {"tokensPerAnswer": None}},
                   "curvas": curves}
        text = render(summary)
        self.assertEqual(text.count("no válida: errores"), 2)


if __name__ == "__main__":
    unittest.main()
