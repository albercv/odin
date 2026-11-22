"""Batería del encargo 30 (Mirador): carga, M9 fuera de la medición autorizada y patrones no_debe_afirmar. Sin IA."""
import json
import unittest
from pathlib import Path

from banco import config, run, scoring
from banco.layout import default_layout

BANCO = Path(__file__).resolve().parent
ITEMS = json.loads((BANCO / "baterias" / "encargo-30-mirador.json").read_text(encoding="utf-8"))
BY_ID = {i["id"]: i for i in ITEMS}


def _ans(text):
    return {"respuesta": text}


class Encargo30Test(unittest.TestCase):
    def test_loads_nine_questions_with_unique_ids(self):
        self.assertEqual([i["id"] for i in ITEMS], [f"E30-M{n}" for n in range(1, 10)])
        self.assertTrue(all(i["pregunta"] and i["claves"] for i in ITEMS))

    def test_only_m9_is_future(self):
        self.assertEqual([i["id"] for i in ITEMS if i["medicion"] == "futura"], ["E30-M9"])

    def test_registered_battery_loads_through_capas(self):
        from banco import capas
        self.assertEqual(len(capas.load_items(default_layout(), ["encargo-30-mirador"])), 9)

    def test_authorised_config_never_includes_m9(self):
        cfg = config.load_config(BANCO / "configs" / "encargo-30-mirador.toml", BANCO)
        sample = run.draw_sample(ITEMS, cfg.sample.strata, cfg.sample.size, cfg.sample.seed, cfg.sample.include)
        self.assertEqual(sorted(i["id"] for i in sample), [f"E30-M{n}" for n in range(1, 9)])

    def test_m9_config_selects_only_m9(self):
        cfg = config.load_config(BANCO / "configs" / "encargo-30-mirador-m9.toml", BANCO)
        sample = run.draw_sample(ITEMS, cfg.sample.strata, cfg.sample.size, cfg.sample.seed, cfg.sample.include)
        self.assertEqual([i["id"] for i in sample], ["E30-M9"])

    def test_m2_forbidden(self):
        m2 = BY_ID["E30-M2"]
        for bad in ("Hay un desajuste entre las facturas.", "Esto no cuadra.", "Falta una segunda factura.",
                    "Existe una discrepancia."):
            self.assertIsNotNone(scoring.forbidden_hit(m2, _ans(bad)), bad)
        for good in ("Costó 470,69 € (AT-2025-0302-R), que rectifica los 481,58 € de AT-2025-0302.",
                     "La diferencia entre 398 € y 389 € se explica por la rectificativa."):
            self.assertIsNone(scoring.forbidden_hit(m2, _ans(good)), good)

    def test_m9_forbidden(self):
        m9 = BY_ID["E30-M9"]
        for bad in ("VLM-19 tiene superseded: true.", "VLM-19 está marcada como sustituida.",
                    "No hay ninguna póliza vigente sustituida.", "No hay ninguna póliza sustituida."):
            self.assertIsNotNone(scoring.forbidden_hit(m9, _ans(bad)), bad)
        for good in ("Sí: VLM-19-48120 figura como vigente, pero VLM-22-48120 dice que la sustituye (sin confirmar).",
                     "VLM-16 y VLM-22 constan como sustituidas por VLM-19 y VLM-25."):
            self.assertIsNone(scoring.forbidden_hit(m9, _ans(good)), good)

    def test_completed_questions_have_no_placeholder_and_forbidden_patterns(self):
        self.assertNotIn("por completar", json.dumps(ITEMS, ensure_ascii=False))
        for q, bad, good in (
            ("E30-M6", "El acuerdo de 2023 es nulo.", "Sí, desde el 1 de abril de 2023 (acta del 23 de marzo de 2023); no consta qué artículo cambia."),
            ("E30-M8", "El local L1 debe 936 euros.", "No consta."),
            ("E30-M7", "Antes era otro número de contrato.", "Es TRL-2025-0388; no consta ninguna modificación."),
            ("E30-M5", "La derrama se paga en 12 cuotas de 18.000 €.", "Son 18 cuotas en total; las 9 últimas de 6.000 €."),
        ):
            self.assertIsNotNone(scoring.forbidden_hit(BY_ID[q], _ans(bad)), (q, bad))
            self.assertIsNone(scoring.forbidden_hit(BY_ID[q], _ans(good)), (q, good))
        self.assertEqual(BY_ID["E30-M8"]["actor"], "t02")


if __name__ == "__main__":
    unittest.main()
