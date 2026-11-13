import tempfile
import unittest
from pathlib import Path

from banco.config import ConfigError, load_config

BANCO = Path(__file__).resolve().parent
CONFIGS = BANCO / "configs"

VALID = """
name = "prueba"
archives = ["ficticio"]
battery = "desarrollo"
mode = "explore"
sample = { size = 5, strata = "tipo", seed = 7 }
repetitions = 1
budgetUsd = 1.5
concurrency = 4

[[searchers]]
approach = "main"
count = 10
model = "haiku"

[[searchers]]
approach = "topic"
count = 5
model = "haiku"

[combiner]
kind = "composer"
model = "haiku"

[[judges]]
rubric = "strict"
count = 3
model = "haiku"

[[judges]]
rubric = "strict"
count = 2
model = "ollama:qwen3.5:9b"

[judging]
aggregation = "majority"
"""


def variant(old: str, new: str) -> str:
    assert old in VALID, old
    return VALID.replace(old, new, 1)


def load_text(text: str):
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "config.toml"
        path.write_text(text, encoding="utf-8")
        return load_config(path, BANCO)


class ConfigTest(unittest.TestCase):
    def test_loads_example(self):
        cfg = load_config(CONFIGS / "ejemplo.toml", BANCO)
        self.assertEqual(len(cfg.searchers), 3)
        self.assertEqual(sum(s.count for s in cfg.searchers), 20)
        self.assertEqual(cfg.budget_usd, 6.0)
        self.assertEqual(cfg.aggregation, "majority")
        self.assertIsNone(cfg.max_turns)

    def test_loads_piloto(self):
        cfg = load_config(CONFIGS / "piloto.toml", BANCO)
        self.assertEqual(cfg.sample.size, 5)
        self.assertEqual(cfg.batteries, ("desarrollo",))
        self.assertEqual(sum(j.count for j in cfg.judges), 5)

    def test_max_turns_is_read_when_present(self):
        cfg = load_text(variant("concurrency = 4", "concurrency = 4\nmaxTurns = 20"))
        self.assertEqual(cfg.max_turns, 20)

    def test_reserve_requires_closing(self):
        with self.assertRaisesRegex(ConfigError, "la reserva solo se usa en modo closing"):
            load_text(variant('battery = "desarrollo"', 'battery = "reserva"'))

    def test_closing_forbids_sample(self):
        with self.assertRaisesRegex(ConfigError, "closing"):
            load_text(variant('mode = "explore"', 'mode = "closing"'))

    def test_closing_without_sample_loads(self):
        text = variant('mode = "explore"', 'mode = "closing"')
        text = text.replace('sample = { size = 5, strata = "tipo", seed = 7 }\n', "")
        self.assertIsNone(load_text(text).sample)

    def test_battery_list_loads_in_order(self):
        text = VALID.replace('archives = ["ficticio"]', 'archives = ["ficticio", "generalidad"]').replace(
            'battery = "desarrollo"', 'battery = ["generalidad", "desarrollo"]')
        self.assertEqual(load_text(text).batteries, ("generalidad", "desarrollo"))

    def test_battery_list_needs_its_archives(self):
        text = VALID.replace('battery = "desarrollo"', 'battery = ["generalidad"]')
        with self.assertRaisesRegex(ConfigError, "necesita el archivo «generalidad»"):
            load_text(text)

    def test_battery_list_rejects_duplicates_and_unknown_names(self):
        with self.assertRaisesRegex(ConfigError, "repite"):
            load_text(VALID.replace('battery = "desarrollo"', 'battery = ["desarrollo", "desarrollo"]'))
        with self.assertRaisesRegex(ConfigError, "desconocida «nada»"):
            load_text(VALID.replace('battery = "desarrollo"', 'battery = ["nada"]'))
        with self.assertRaisesRegex(ConfigError, "al menos una batería"):
            load_text(VALID.replace('battery = "desarrollo"', 'battery = []'))

    def test_reserve_in_a_list_requires_closing(self):
        with self.assertRaisesRegex(ConfigError, "la reserva solo se usa en modo closing"):
            load_text(VALID.replace('battery = "desarrollo"', 'battery = ["desarrollo", "reserva"]'))

    def test_unknown_approach_file(self):
        with self.assertRaises(ConfigError):
            load_text(variant('approach = "main"', 'approach = "nada"'))

    def test_approach_name_must_be_plain(self):
        with self.assertRaises(ConfigError):
            load_text(variant('approach = "main"', 'approach = "../configs/ejemplo"'))

    def test_at_least_k_bounds(self):
        with self.assertRaises(ConfigError):
            load_text(variant('aggregation = "majority"', 'aggregation = "atLeast:6"'))
        cfg = load_text(variant('aggregation = "majority"', 'aggregation = "atLeast:3"'))
        self.assertEqual(cfg.aggregation, "atLeast:3")

    def test_counts_and_budget_positive(self):
        with self.assertRaises(ConfigError):
            load_text(variant("count = 10", "count = 0"))
        with self.assertRaises(ConfigError):
            load_text(variant("budgetUsd = 1.5", "budgetUsd = 0"))

    def test_ollama_only_for_judges(self):
        old = 'approach = "topic"\ncount = 5\nmodel = "haiku"'
        new = 'approach = "topic"\ncount = 5\nmodel = "ollama:qwen3.5:9b"'
        with self.assertRaises(ConfigError):
            load_text(variant(old, new))

    def test_judge_select_is_rejected(self):
        with self.assertRaises(ConfigError):
            load_text(variant('kind = "composer"', 'kind = "judgeSelect"'))

    def test_malformed_tables_raise_config_error(self):
        cases = [
            ('[combiner]\nkind = "composer"\nmodel = "haiku"', 'combiner = "x"'),
            ('[judging]\naggregation = "majority"', "judging = 5"),
            ('sample = { size = 5, strata = "tipo", seed = 7 }', "sample = 5"),
        ]
        for old, root_line in cases:
            # Las claves de la raíz van antes de las tablas: se escriben al principio.
            text = root_line + "\n" + variant(old, "")
            with self.subTest(line=root_line), self.assertRaises(ConfigError):
                load_text(text)

    def test_non_finite_budget_is_rejected(self):
        for value in ("nan", "inf"):
            with self.subTest(value=value), self.assertRaises(ConfigError):
                load_text(variant("budgetUsd = 1.5", f"budgetUsd = {value}"))

    def test_judge_think_and_timeout_defaults_for_ollama(self):
        ollama = load_text(VALID).judges[1]
        self.assertFalse(ollama.think)
        self.assertEqual(ollama.timeout_s, 600)

    def test_judge_think_and_timeout_are_read_for_ollama(self):
        text = variant('model = "ollama:qwen3.5:9b"', 'model = "ollama:qwen3.5:9b"\nthink = true\ntimeoutS = 90')
        ollama = load_text(text).judges[1]
        self.assertTrue(ollama.think)
        self.assertEqual(ollama.timeout_s, 90)

    def test_think_and_timeout_only_apply_to_ollama_judges(self):
        for extra in ("think = false", "timeoutS = 90"):
            with self.subTest(extra=extra), self.assertRaisesRegex(ConfigError, "solo.*ollama"):
                load_text(variant('count = 3\nmodel = "haiku"', f'count = 3\nmodel = "haiku"\n{extra}'))

    def test_think_and_timeout_are_validated(self):
        with self.assertRaises(ConfigError):
            load_text(variant('model = "ollama:qwen3.5:9b"', 'model = "ollama:qwen3.5:9b"\nthink = "no"'))
        with self.assertRaises(ConfigError):
            load_text(variant('model = "ollama:qwen3.5:9b"', 'model = "ollama:qwen3.5:9b"\ntimeoutS = 0'))

    def test_ai_combiner_needs_model_and_single_does_not(self):
        with self.assertRaises(ConfigError):
            load_text(variant('kind = "composer"\nmodel = "haiku"', 'kind = "composer"'))
        cfg = load_text(variant('kind = "composer"\nmodel = "haiku"', 'kind = "single"'))
        self.assertIsNone(cfg.combiner.model)


if __name__ == "__main__":
    unittest.main()
