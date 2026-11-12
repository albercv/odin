"""Pruebas del libro de gasto compartido: el tope se decide antes de lanzar y un coste desconocido agota el presupuesto."""
import json
import tempfile
import threading
import unittest
from pathlib import Path

from banco.ledger import BudgetAlreadySpent, Ledger, open_ledger


class LedgerTest(unittest.TestCase):
    def test_launches_until_cap_is_reached(self):
        ledger = Ledger(0.002)
        self.assertTrue(ledger.can_launch())
        ledger.charge(0.001)
        self.assertTrue(ledger.can_launch())
        ledger.charge(0.001)
        self.assertFalse(ledger.can_launch())

    def test_unknown_cost_exhausts_budget_and_is_reported(self):
        ledger = Ledger(10.0)
        ledger.charge(None)
        self.assertFalse(ledger.can_launch())
        self.assertIn("coste desconocido", ledger.stop_message())

    def test_stop_message_has_spent_and_cap(self):
        ledger = Ledger(0.5)
        ledger.charge(0.25)
        self.assertIn("gastado 0.2500 USD de un tope de 0.5000 USD", ledger.stop_message(skipped=3))

    def test_concurrent_charges_are_not_lost(self):
        ledger = Ledger(100.0)
        threads = [threading.Thread(target=lambda: [ledger.charge(0.001) for _ in range(200)]) for _ in range(8)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        self.assertAlmostEqual(ledger.spent, 1.6)


class PersistedLedgerTest(unittest.TestCase):
    """El libro del experimento sobrevive a las órdenes: lo gastado antes cuenta al relanzar."""

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.path = Path(tmp.name) / "libro-prueba.json"

    def test_charges_are_written_and_survive_a_new_ledger(self):
        open_ledger(self.path, 1.0).charge(0.25)
        data = json.loads(self.path.read_text(encoding="utf-8"))
        self.assertAlmostEqual(data["total_usd"], 0.25)
        self.assertEqual(data["cargos"][0]["coste_usd"], 0.25)
        self.assertIn("fecha", data["cargos"][0])
        self.assertAlmostEqual(open_ledger(self.path, 1.0).spent, 0.25)

    def test_unknown_cost_is_persisted(self):
        open_ledger(self.path, 1.0).charge(None)
        with self.assertRaisesRegex(BudgetAlreadySpent, "coste desconocido"):
            open_ledger(self.path, 1.0)

    def test_already_spent_refuses_to_start(self):
        open_ledger(self.path, 0.5).charge(0.5)
        with self.assertRaisesRegex(BudgetAlreadySpent, "ya gastó 0.5000 USD de un tope de 0.5000 USD"):
            open_ledger(self.path, 0.5)

    def test_reset_archives_the_previous_book_and_starts_at_zero(self):
        open_ledger(self.path, 0.5).charge(0.5)
        ledger = open_ledger(self.path, 0.5, reset=True)
        self.assertEqual(ledger.spent, 0.0)
        self.assertTrue(ledger.can_launch())
        archived = [p for p in self.path.parent.glob("libro-prueba.*.json")]
        self.assertEqual(len(archived), 1)
        self.assertAlmostEqual(json.loads(archived[0].read_text(encoding="utf-8"))["total_usd"], 0.5)


if __name__ == "__main__":
    unittest.main()
