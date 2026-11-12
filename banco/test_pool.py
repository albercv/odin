"""Pruebas del grupo de buscadores con caché y tope de gasto, con un claude falso (nunca el real)."""
import contextlib
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from banco.cache import RunCache, run_key
from banco.config import SearcherSpec
from banco.medir_path import ejecutar
from banco.pool import BudgetExceeded, PoolSettings, PoolStopped, run_pool

FAKE = str(Path(__file__).resolve().parent / "fakes" / "fake_claude_turno.py")
ITEMS = [{"id": "Q001", "actor": "t01", "pregunta": "¿Cuánto cuesta?"}, {"id": "Q002", "actor": "t01", "pregunta": "¿Quién?"}]
CARGA = {"url_mcp": "http://127.0.0.1:1/", "claves": {"t01": "k1"}, "doc_ruta": {}, "ficha_f": {}}
SPECS = (SearcherSpec("main", 3, "haiku"), SearcherSpec("topic", 2, "haiku"))
Q1 = "¿Cuánto cuesta?"


@contextlib.contextmanager
def fake_env(**env):
    """Entorno sin variables FAKE_ heredadas, más las indicadas."""
    base = {k: v for k, v in os.environ.items() if not k.startswith("FAKE_")}
    with mock.patch.dict(os.environ, base, clear=True):
        os.environ.update(env)
        yield


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
        registros = self._run(concurrency=3)
        self.assertEqual(len(registros), 10)
        self.assertEqual(sum(r["approach"] == "main" for r in registros), 6)
        self.assertEqual(sum(r["approach"] == "topic" for r in registros), 4)
        self.assertTrue(all(r["error"] is None and r["cached"] is False for r in registros))

    def test_second_run_hits_cache(self):
        self._run()
        registros = self._run(budget=0.0001)  # con la caché no se lanza nada: no cuenta para el tope
        self.assertEqual(len(registros), 10)
        self.assertTrue(all(r["cached"] for r in registros))

    def test_failed_run_is_not_cached(self):
        registros = self._run(specs=(SearcherSpec("main", 1, "haiku"),), items=ITEMS[:1], FAKE_MODO="error")
        key = run_key("Q001", Q1, "main", "", "haiku", 0, "abc", None)
        self.assertIsNotNone(registros[0]["error"])
        self.assertIsNone(self.cache.get(key))

    def test_budget_stops_new_launches(self):
        with self.assertRaises(BudgetExceeded):
            self._run(budget=0.0015, specs=(SearcherSpec("main", 5, "haiku"),), items=ITEMS[:1])
        self.assertEqual(len(self._cached_files()), 2)  # cada ejecución cuesta 0.001: se lanzan 2
        self.assertIn("gastado 0.0020 USD de un tope de 0.0015 USD", self._stop_text())

    def test_budget_overshoot_with_concurrency_is_bounded(self):
        with self.assertRaises(BudgetExceeded):
            self._run(budget=0.0015, concurrency=3, specs=(SearcherSpec("main", 5, "haiku"),), items=ITEMS[:1])
        lanzadas = len(self._cached_files())
        self.assertGreaterEqual(lanzadas, 2)
        self.assertLessEqual(lanzadas, 2 + (3 - 1))  # el tope se comprueba antes de cada lanzamiento
        self.assertIn(f"gastado {lanzadas * 0.001:.4f} USD de un tope de 0.0015 USD", self._stop_text())

    def test_quota_limit_stops_pool_and_resume_uses_cache(self):
        contador = self.dir / "contador"
        specs = (SearcherSpec("main", 4, "haiku"),)
        with self.assertRaises(PoolStopped):
            self._run(specs=specs, items=ITEMS[:1], FAKE_CONTADOR=str(contador), FAKE_MODO_DESDE="3",
                      FAKE_MODO_DESDE_MODO="limite")
        self.assertIn("Q001", self._stop_text())
        self.assertEqual(contador.read_text(), "3")  # la cuarta no llega a lanzarse
        self.assertEqual(len(self._cached_files()), 2)
        registros = self._run(specs=specs, items=ITEMS[:1], FAKE_CONTADOR=str(contador))
        self.assertEqual(sum(r["cached"] for r in registros), 2)
        self.assertTrue(all(r["error"] is None for r in registros))

    def test_isolation_breach_stops_everything(self):
        with self.assertRaises(ejecutar.Aislamiento):
            self._run(FAKE_MODO="tools_extra")
        self.assertIn("AISLAMIENTO", self._stop_text())
        self.assertEqual(self._cached_files(), [])

    def test_unexpected_error_stops_pool_and_propagates(self):
        real_put = RunCache.put
        llamadas = []

        def fallar_una_vez(cache, key, record):
            llamadas.append(key)
            if len(llamadas) == 1:
                raise OSError("disco lleno")
            real_put(cache, key, record)

        contador = self.dir / "contador"
        with mock.patch.object(RunCache, "put", fallar_una_vez), self.assertRaises(OSError):
            self._run(specs=(SearcherSpec("main", 5, "haiku"),), items=ITEMS[:1], FAKE_CONTADOR=str(contador),
                      FAKE_MODO_DESDE="99", FAKE_MODO_DESDE_MODO="ok")
        self.assertEqual(contador.read_text(), "1")  # ninguna ejecución más tras el fallo
        self.assertIn("ERROR INESPERADO", self._stop_text())

    def test_approach_text_changes_key(self):
        antes = run_key("Q001", Q1, "topic", "Busca por tema.", "haiku", 0, "abc", None)
        despues = run_key("Q001", Q1, "topic", "Otro texto.", "haiku", 0, "abc", None)
        self.assertNotEqual(antes, despues)

    def test_max_turns_changes_key(self):
        diez = run_key("Q001", Q1, "main", "", "haiku", 0, "abc", 10)
        veinte = run_key("Q001", Q1, "main", "", "haiku", 0, "abc", 20)
        self.assertNotEqual(diez, veinte)

    def test_question_text_changes_key(self):
        antes = run_key("Q001", Q1, "main", "", "haiku", 0, "abc", None)
        despues = run_key("Q001", "¿Cuánto cuesta ahora?", "main", "", "haiku", 0, "abc", None)
        self.assertNotEqual(antes, despues)


if __name__ == "__main__":
    unittest.main()
