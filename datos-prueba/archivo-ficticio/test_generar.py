"""El generador del archivo ficticio es determinista: dos ejecuciones dan lo mismo y la huella de datos/ no cambia
sin querer. Si cambias el generador a propósito, pon en HUELLA la que da el fallo de esta prueba."""
import hashlib
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

AQUI = Path(__file__).resolve().parent
HUELLA = "ee8cb960e9461df7cac61b904310f59ae418f4257efdc397b2cf3e6272b856bb"


def huella(datos):
    h = hashlib.sha256()
    for f in sorted(p for p in datos.rglob("*") if p.is_file()):
        h.update(f.relative_to(datos).as_posix().encode() + b"\0" + f.read_bytes() + b"\0")
    return h.hexdigest()


def generar(destino):
    """Copia generar/ y vendor/ a destino (el generador escribe junto a sí mismo) y lo ejecuta ahí."""
    for carpeta in ("generar", "vendor"):
        shutil.copytree(AQUI / carpeta, destino / carpeta, ignore=shutil.ignore_patterns("__pycache__"))
    subprocess.run([sys.executable, str(destino / "generar" / "generar.py")], check=True, capture_output=True)
    return destino / "datos"


class TestGenerador(unittest.TestCase):
    def test_determinista_y_huella_fija(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            ha, hb = huella(generar(Path(a))), huella(generar(Path(b)))
        self.assertEqual(ha, hb)
        self.assertEqual(HUELLA, ha)


if __name__ == "__main__":
    unittest.main()
