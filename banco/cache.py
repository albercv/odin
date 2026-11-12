"""Caché de ejecuciones en disco: una entrada JSON por clave, escrita de forma atómica."""

import hashlib
import json
import os
import tempfile
from pathlib import Path


def _digest(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def run_key(question_id: str, question_text: str, approach: str, approach_text: str, model: str, replica: int,
            odin_commit: str, max_turns: int | None, stack_digest: str = "") -> str:
    """Clave de una ejecución: pregunta, enfoque, modelo, réplica, huella del código, tope de turnos y mapas de ids."""
    parts = [question_id, _digest(question_text), approach, _digest(approach_text), model, str(replica),
             odin_commit, str(max_turns), stack_digest]
    return hashlib.sha256(json.dumps(parts).encode("utf-8")).hexdigest()


class RunCache:
    def __init__(self, root: Path):
        self.root = Path(root)

    def _path(self, key: str) -> Path:
        return self.root / f"{key}.json"

    def get(self, key: str) -> dict | None:
        path = self._path(key)
        return json.loads(path.read_text(encoding="utf-8")) if path.exists() else None

    def put(self, key: str, record: dict) -> None:
        if record.get("error") is not None:
            return  # los fallos se relanzan, no se guardan
        self.root.mkdir(parents=True, exist_ok=True)
        fd, tmp = tempfile.mkstemp(dir=self.root, suffix=".tmp")
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(record, f, ensure_ascii=False)
        os.replace(tmp, self._path(key))
