"""Pruebas del grupo de buscadores con caché y tope de gasto, con un claude falso (nunca el real)."""
import contextlib
import itertools
import os
import tempfile
import threading
import unittest
from pathlib import Path
from unittest import mock

from banco.cache import RunCache, run_key
from banco.config import SearcherSpec
from banco.ledger import Ledger
from banco.medir_path import ejecutar
from banco.pool import BudgetExceeded, PoolSettings, PoolStopped, run_pool

FAKE = str(Path(__file__).resolve().parent / "fakes" / "fake_claude_turno.py")
ITEMS = [{"id": "Q001", "actor": "t01", "pregunta": "¿Cuánto cuesta?"}, {"id": "Q002", "actor": "t01", "pregunta": "¿Quién?"}]
CARGA = {"url_mcp": "http://127.0.0.1:1/", "claves": {"t01": "k1"}, "doc_ruta": {}, "ficha_f": {}}
SPECS = (SearcherSpec("main", 3, "haiku"), SearcherSpec("topic", 2, "haiku"))
QUESTION = "¿Cuánto cuesta?"


@contextlib.contextmanager
def fake_env(**env):
    """Entorno sin variables FAKE_ heredadas, más las indicadas."""
    base = {k: v for k, v in os.environ.items() if not k.startswith("FAKE_")}
    with mock.patch.dict(os.environ, base, clear=True):
        os.environ.update(env)
        yield


def _launches(counter):
    """Número de llamadas al falso claude (una línea por llamada)."""
    return len(counter.read_text().splitlines()) if counter.exists() else 0


class PoolTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.dir = Path(tmp.name)
        (self.dir / "enfoques").mkdir()
        (self.dir / "enfoques" / "main.txt").write_text("")
        (self.dir / "enfoques" / "topic.txt").write_text("Busca por tema.")
        self.cache = RunCache(self.dir / "cache")
        self.stop = self.dir / "PARADA.txt"
        self.counter = self.dir / "contador"

    def _run(self, specs=SPECS, items=ITEMS, budget=100.0, concurrency=1, **env):
        settings = PoolSettings(claude_bin=FAKE, timeout=30, concurrency=concurrency, budget_usd=budget,
                                max_turns=None, odin_commit="abc")
        with fake_env(**env):
            return run_pool(items, specs, CARGA, self.cache, settings, self.dir / "enfoques", self.stop)

    def _cached_files(self):
        return list(self.cache.root.glob("*.json"))

    def _stop_text(self):
        return self.stop.read_text(encoding="utf-8")

    def test_runs_count_times_per_spec(self):
        records = self._run(concurrency=3)
        self.assertEqual(len(records), 10)
        self.assertEqual(sum(r["approach"] == "main" for r in records), 6)
        self.assertEqual(sum(r["approach"] == "topic" for r in records), 4)
        self.assertTrue(all(r["error"] is None and r["cached"] is False for r in records))

    def test_stop_carries_the_records_already_made_including_failed_ones(self):
        real = ejecutar.responder
        calls = []

        def second_hits_quota(*args, **kwargs):
            calls.append(1)
            if len(calls) == 2:
                return {"id": "Q001", "actor": "t01", "error": "Claude AI usage limit reached", "coste_usd": 0.0,
                        "duracion_s": 1.0, "respuesta": None, "consultas": [], "tokens": None}
            return real(*args, **kwargs)

        with mock.patch("banco.medir_path.ejecutar.responder", side_effect=second_hits_quota):
            with self.assertRaises(PoolStopped) as caught:
                self._run(specs=(SearcherSpec("main", 3, "haiku"),))
        records = caught.exception.records
        self.assertEqual(len(records), 2)
        self.assertEqual(sum(r["error"] is not None for r in records), 1)

    def test_sin_clave_no_gasta_y_no_es_coste_desconocido(self):
        settings = PoolSettings(claude_bin=FAKE, timeout=30, concurrency=1, budget_usd=100.0, max_turns=None, odin_commit="abc")
        ledger = Ledger(100.0)
        records = run_pool(ITEMS, (SearcherSpec("main", 1, "haiku"),), {**CARGA, "claves": {}}, self.cache, settings,
                           self.dir / "enfoques", self.stop, ledger=ledger)
        self.assertEqual(len(records), 2)
        self.assertTrue(all(r["error"] and r["coste_usd"] == 0.0 for r in records))
        self.assertEqual((ledger.unknown, ledger.spent), (0, 0.0))
        self.assertFalse(self.stop.exists())

    def test_timeout_keeps_cost_unknown(self):
        def timed_out(*args, **kwargs):
            return {"id": "Q001", "actor": "t01", "error": "tiempo agotado (30 s)", "coste_usd": None, "duracion_s": 30.0,
                    "respuesta": None, "consultas": [], "tokens": None}

        ledger = Ledger(100.0)
        with mock.patch("banco.medir_path.ejecutar.responder", side_effect=timed_out):
            with self.assertRaises(BudgetExceeded):
                run_pool(ITEMS, (SearcherSpec("main", 1, "haiku"),), CARGA, self.cache,
                         PoolSettings(claude_bin=FAKE, timeout=30, concurrency=1, budget_usd=100.0, max_turns=None,
                                      odin_commit="abc"), self.dir / "enfoques", self.stop, ledger=ledger)
        self.assertEqual(ledger.unknown, 1)

    def test_second_run_hits_cache(self):
        self._run()
        records = self._run(budget=0.0001)  # con la caché no se lanza nada: no cuenta para el tope
        self.assertEqual(len(records), 10)
        self.assertTrue(all(r["cached"] for r in records))

    def test_failed_run_is_not_cached(self):
        records = self._run(specs=(SearcherSpec("main", 1, "haiku"),), items=ITEMS[:1], FAKE_MODO="error")
        key = run_key("Q001", QUESTION, "main", "", "haiku", 0, "abc", None)
        self.assertIsNotNone(records[0]["error"])
        self.assertIsNone(self.cache.get(key))

    def test_budget_stops_new_launches(self):
        with self.assertRaises(BudgetExceeded):
            self._run(budget=0.0015, specs=(SearcherSpec("main", 5, "haiku"),), items=ITEMS[:1])
        self.assertEqual(len(self._cached_files()), 2)  # cada ejecución cuesta 0.001: se lanzan 2
        self.assertIn("gastado 0.0020 USD de un tope de 0.0015 USD", self._stop_text())

    def test_budget_overshoot_with_concurrency_is_bounded(self):
        with self.assertRaises(BudgetExceeded):
            self._run(budget=0.0015, concurrency=3, specs=(SearcherSpec("main", 5, "haiku"),), items=ITEMS[:1])
        launched = len(self._cached_files())
        self.assertGreaterEqual(launched, 2)
        self.assertLessEqual(launched, 2 + (3 - 1))  # el tope se comprueba antes de cada lanzamiento
        self.assertIn(f"gastado {launched * 0.001:.4f} USD de un tope de 0.0015 USD", self._stop_text())

    def test_quota_limit_stops_pool_and_resume_uses_cache(self):
        specs = (SearcherSpec("main", 4, "haiku"),)
        with self.assertRaises(PoolStopped):
            self._run(specs=specs, items=ITEMS[:1], FAKE_CONTADOR=str(self.counter), FAKE_MODO_DESDE="3",
                      FAKE_MODO_DESDE_MODO="limite")
        self.assertIn("Q001", self._stop_text())
        self.assertEqual(_launches(self.counter), 3)  # la cuarta no llega a lanzarse
        self.assertEqual(len(self._cached_files()), 2)
        records = self._run(specs=specs, items=ITEMS[:1], FAKE_CONTADOR=str(self.counter))
        self.assertEqual(sum(r["cached"] for r in records), 2)
        self.assertTrue(all(r["error"] is None for r in records))

    def test_no_launch_after_limit_with_concurrency(self):
        with self.assertRaises(PoolStopped):
            self._run(specs=(SearcherSpec("main", 6, "haiku"),), items=ITEMS[:1], concurrency=3,
                      FAKE_CONTADOR=str(self.counter), FAKE_MODO_DESDE="1", FAKE_MODO_DESDE_MODO="limite",
                      FAKE_SLEEP="0.5")
        # las tres primeras se decidieron antes de que volviera el primer límite; las demás, no
        self.assertEqual(_launches(self.counter), 3)

    def test_held_job_does_not_launch_after_stop(self):
        gate = threading.Event()
        order = itertools.count()
        real_get, real_put = RunCache.get, RunCache.put

        def get_holding_first(cache, key):
            if next(order) == 0:
                gate.wait(5)  # una tarea se queda justo antes de decidir si lanza
            return real_get(cache, key)

        def put_releasing(cache, key, record):
            if record["error"]:
                gate.set()  # el parado ya está escrito cuando se guarda el fallo
            real_put(cache, key, record)

        with mock.patch.object(RunCache, "get", get_holding_first), \
                mock.patch.object(RunCache, "put", put_releasing), self.assertRaises(PoolStopped):
            self._run(specs=(SearcherSpec("main", 2, "haiku"),), items=ITEMS[:1], concurrency=2,
                      FAKE_CONTADOR=str(self.counter), FAKE_MODO_DESDE="1", FAKE_MODO_DESDE_MODO="limite")
        self.assertEqual(_launches(self.counter), 1)

    def test_isolation_breach_stops_everything(self):
        with self.assertRaises(ejecutar.Aislamiento):
            self._run(FAKE_MODO="tools_extra")
        self.assertIn("AISLAMIENTO", self._stop_text())
        self.assertEqual(self._cached_files(), [])

    def test_unexpected_error_stops_pool_and_propagates(self):
        real_put = RunCache.put
        calls = []

        def fail_once(cache, key, record):
            calls.append(key)
            if len(calls) == 1:
                raise OSError("disco lleno")
            real_put(cache, key, record)

        with mock.patch.object(RunCache, "put", fail_once), self.assertRaises(OSError):
            self._run(specs=(SearcherSpec("main", 5, "haiku"),), items=ITEMS[:1], FAKE_CONTADOR=str(self.counter),
                      FAKE_MODO_DESDE="99", FAKE_MODO_DESDE_MODO="ok")
        self.assertEqual(_launches(self.counter), 1)  # ninguna ejecución más tras el fallo
        self.assertIn("ERROR INESPERADO", self._stop_text())

    def test_approach_text_changes_key(self):
        before = run_key("Q001", QUESTION, "topic", "Busca por tema.", "haiku", 0, "abc", None)
        after = run_key("Q001", QUESTION, "topic", "Otro texto.", "haiku", 0, "abc", None)
        self.assertNotEqual(before, after)

    def test_max_turns_changes_key(self):
        ten = run_key("Q001", QUESTION, "main", "", "haiku", 0, "abc", 10)
        twenty = run_key("Q001", QUESTION, "main", "", "haiku", 0, "abc", 20)
        self.assertNotEqual(ten, twenty)

    def test_question_text_changes_key(self):
        before = run_key("Q001", QUESTION, "main", "", "haiku", 0, "abc", None)
        after = run_key("Q001", "¿Cuánto cuesta ahora?", "main", "", "haiku", 0, "abc", None)
        self.assertNotEqual(before, after)


if __name__ == "__main__":
    unittest.main()
