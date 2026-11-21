"""Shared helpers for the search experiment: page parsing and text normalisation."""
import json
import os
import re
import unicodedata
from pathlib import Path
from urllib.request import urlopen

EXP = Path(__file__).resolve().parent.parent
CORPUS = Path(os.environ.get("BDM_CORPUS", EXP / "corpus"))
CLAVE = EXP.parent / "clave-bdm"
CLEAN_CORPUS = CLAVE / "corpus-limpio"
BATTERY = Path(os.environ.get("BDM_BATERIA", CLAVE / "bateria_resuelta.json"))
RESULTS = Path(os.environ.get("BDM_RESULTADOS", EXP / "resultados"))
PAGE_MARK = re.compile(r"^=== Página (\d+) ===\s*$", re.MULTILINE)


def read_pages(path):
    """Return {page_number: text} for a corpus file."""
    text = Path(path).read_text(encoding="utf-8")
    parts = PAGE_MARK.split(text)
    return {int(parts[i]): parts[i + 1].strip() for i in range(1, len(parts) - 1, 2)}


def load_corpus(root=CORPUS):
    """Return {doc_id: {page: text}} with doc_id relative to root."""
    return {str(p.relative_to(root)): read_pages(p) for p in sorted(root.rglob("*.txt"))}


def strip_accents(text):
    return "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn")


def normalise(text):
    """Lowercase, no accents, single spaces, no thousands dots (48.000 -> 48000)."""
    text = strip_accents(text.lower())
    text = re.sub(r"(?<=\d)\.(?=\d{3}\b)", "", text)
    return re.sub(r"\s+", " ", text).strip()


def ocr_tolerant(text, numeric):
    """Undo typical OCR confusions so a key can be matched in a noisy answer."""
    table = str.maketrans("oOlIsSbB", "00115588") if numeric else str.maketrans("0158", "olsb")
    return text.translate(table)


def loaded_model(name):
    """Ollama's /api/ps entry for a loaded model: total memory ('size') and the part on the GPU ('size_vram')."""
    models = json.loads(urlopen("http://127.0.0.1:11434/api/ps", timeout=30).read())["models"]
    return next(m for m in models if m["name"] == name)


def load_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))
