"""Pruebas de las claves de caché del redactor y de los veredictos, y de lo que la caché no guarda."""
import json
import tempfile
import unittest
from pathlib import Path

from banco.cache import RunCache, final_key, verdict_key

ANSWER = {"respuesta": "Cuesta 10 €.", "no_lo_se": False, "citas": [{"documentId": "d1", "cardId": None, "pagina": 2}]}


def _final(**changes):
    args = {"question_id": "Q1", "question_text": "¿Cuánto?", "kind": "composer", "model": "haiku",
            "input_keys": ["b", "a"], "odin_commit": "abc", "stack_digest": "s1"}
    return final_key(**{**args, **changes})


def _verdict(**changes):
    args = {"answer": ANSWER, "rubric_text": "Eres un juez.", "model": "haiku", "judge_index": 0, "seed": 7}
    return verdict_key(**{**args, **changes})


class FinalKeyTest(unittest.TestCase):
    def test_same_inputs_same_key_and_input_order_does_not_matter(self):
        self.assertEqual(_final(), _final(input_keys=["a", "b"]))

    def test_each_part_changes_the_key(self):
        base = _final()
        for changes in ({"question_id": "Q2"}, {"question_text": "otra"}, {"kind": "pageFusion"}, {"model": "sonnet"},
                        {"input_keys": ["a"]}, {"odin_commit": "def"}, {"stack_digest": "s2"}):
            with self.subTest(changes=changes):
                self.assertNotEqual(base, _final(**changes))


class VerdictKeyTest(unittest.TestCase):
    def test_same_inputs_same_key(self):
        self.assertEqual(_verdict(), _verdict(answer=json.loads(json.dumps(ANSWER))))

    def test_each_part_changes_the_key(self):
        base = _verdict()
        for changes in ({"answer": {**ANSWER, "respuesta": "otra"}}, {"rubric_text": "otra"}, {"model": "sonnet"},
                        {"judge_index": 1}, {"seed": 8}):
            with self.subTest(changes=changes):
                self.assertNotEqual(base, _verdict(**changes))


class RunCacheTest(unittest.TestCase):
    def test_records_with_error_are_not_stored(self):
        with tempfile.TemporaryDirectory() as tmp:
            cache = RunCache(Path(tmp))
            cache.put("k1", {"error": "Claude AI usage limit reached", "respuesta": None})
            cache.put("k2", {"error": None, "respuesta": {"respuesta": "x"}})
            self.assertIsNone(cache.get("k1"))
            self.assertEqual(cache.get("k2"), {"error": None, "respuesta": {"respuesta": "x"}})


if __name__ == "__main__":
    unittest.main()
