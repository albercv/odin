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

    def _run(self, specs=SPECS, items=ITEMS, budget=100.0, concurrencia=1, **env):
        settings = PoolSettings(claude_bin=FAKE, timeout=30, concurrency=concurrencia, budget_usd=budget,
                                max_turns=None, odin_commit="abc")
        with fake_env(**env):
            return run_pool(items, specs, CARGA, self.cache, settings, self.dir / "enfoques", self.stop)

    def _cached_files(self):
        return list(self.cache.root.glob("*.json"))

    def test_runs_count_times_per_spec(self):
        registros = self._run(concurrencia=3)
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
        key = run_key("Q001", "main", "", "haiku", 0, "abc")
        self.assertIsNotNone(registros[0]["error"])
        self.assertIsNone(self.cache.get(key))

    def test_budget_stops_new_launches(self):
        with self.assertRaises(BudgetExceeded):
            self._run(budget=0.0015, specs=(SearcherSpec("main", 5, "haiku"),), items=ITEMS[:1])
        self.assertEqual(len(self._cached_files()), 2)  # cada ejecución cuesta 0.001: se lanzan 2
        texto = self.stop.read_text(encoding="utf-8")
        self.assertIn("0.0020", texto)
        self.assertIn("0.0015", texto)

    def test_quota_limit_stops_pool_and_resume_uses_cache(self):
        contador = self.dir / "contador"
        specs = (SearcherSpec("main", 4, "haiku"),)
        with self.assertRaises(PoolStopped):
            self._run(specs=specs, items=ITEMS[:1], FAKE_CONTADOR=str(contador), FAKE_MODO_DESDE="3",
                      FAKE_MODO_DESDE_MODO="limite")
        self.assertIn("Q001", self.stop.read_text(encoding="utf-8"))
        self.assertEqual(contador.read_text(), "3")  # la cuarta no llega a lanzarse
        self.assertEqual(len(self._cached_files()), 2)
        registros = self._run(specs=specs, items=ITEMS[:1], FAKE_CONTADOR=str(contador))
        self.assertEqual(sum(r["cached"] for r in registros), 2)
        self.assertTrue(all(r["error"] is None for r in registros))

    def test_isolation_breach_stops_everything(self):
        with self.assertRaises(ejecutar.Aislamiento):
            self._run(FAKE_MODO="tools_extra")
        self.assertIn("AISLAMIENTO", self.stop.read_text(encoding="utf-8"))
        self.assertEqual(self._cached_files(), [])

    def test_approach_text_changes_key(self):
        antes = run_key("Q001", "topic", "Busca por tema.", "haiku", 0, "abc")
        despues = run_key("Q001", "topic", "Otro texto.", "haiku", 0, "abc")
        self.assertNotEqual(antes, despues)


if __name__ == "__main__":
    unittest.main()
