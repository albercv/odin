"""Caché de ejecuciones en disco: una entrada JSON por clave, escrita de forma atómica."""

import hashlib
import json
import os
import tempfile
from pathlib import Path


def run_key(question_id: str, approach: str, approach_text: str, model: str, replica: int, odin_commit: str) -> str:
    partes = [question_id, approach, hashlib.sha256(approach_text.encode("utf-8")).hexdigest(), model, str(replica), odin_commit]
    return hashlib.sha256(json.dumps(partes).encode("utf-8")).hexdigest()


class RunCache:
    def __init__(self, root: Path):
        self.root = Path(root)

    def _ruta(self, key: str) -> Path:
        return self.root / f"{key}.json"

    def get(self, key: str) -> dict | None:
        ruta = self._ruta(key)
        return json.loads(ruta.read_text(encoding="utf-8")) if ruta.exists() else None

    def put(self, key: str, record: dict) -> None:
        if record.get("error") is not None:
            return  # los fallos se relanzan, no se guardan
        self.root.mkdir(parents=True, exist_ok=True)
        fd, tmp = tempfile.mkstemp(dir=self.root, suffix=".tmp")
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(record, f, ensure_ascii=False)
        os.replace(tmp, self._ruta(key))
