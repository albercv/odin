"""Pruebas del libro de gasto compartido: el tope se decide antes de lanzar y un coste desconocido agota el presupuesto."""
import threading
import unittest

from banco.ledger import Ledger


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


if __name__ == "__main__":
    unittest.main()
