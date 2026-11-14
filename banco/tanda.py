"""Tandas: fichero de reanudación del experimento (banco/estado/reanudar-<nombre>.json), registro de tandas de la
carpeta (batches.json) y escrituras atómicas (temporal y os.replace). Sin IA ni red."""

import json
import os
import re
import tempfile
from pathlib import Path

RENEWAL = re.compile(r"resets\s+(\S+(?:\s*\([^)]*\))?)")


def atomic_write(path, text):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, suffix=".tmp")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(text)
    os.replace(tmp, path)


def state_path(state_dir, name):
    return Path(state_dir) / f"reanudar-{name}.json"


def read_state(path):
    path = Path(path)
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else None


def save_state(path, state):
    atomic_write(path, json.dumps(state, ensure_ascii=False, indent=1))


def delete_state(path):
    Path(path).unlink(missing_ok=True)


def read_batches(folder):
    path = Path(folder) / "batches.json"
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else []


def write_batches(folder, batches):
    atomic_write(Path(folder) / "batches.json", json.dumps(batches, ensure_ascii=False, indent=1))


def renewal_time(text):
    """Hora de renovación que trae el aviso («resets 9:50pm (Europe/Madrid)»), como texto; None si no la trae."""
    match = RENEWAL.search(text or "")
    return match.group(1) if match else None
