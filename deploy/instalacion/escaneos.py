"""Escaneos ficticios de la carga completa: elige 30 documentos de Ribera y los pasa a PDF escaneado con ImageMagick.
Elección determinista (semilla 13). Los PDF se reutilizan si ya existen. Sin `magick`, no se fabrica nada."""
import random
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

SEMILLA, CUANTOS = 13, 30
MARCA = re.compile(r"^=== Página \d+ ===\n", re.M)
MAGICK = shutil.which("magick") or "/opt/homebrew/bin/magick"
A4 = (1654, 2339)  # A4 a 200 ppp


def hay_magick():
    return Path(MAGICK).exists()


def paginas(texto):
    return [p.strip("\n") for p in MARCA.split(texto) if p.strip()]


def tipo(ruta):
    return ruta.split("/")[-1].split("-")[0].removesuffix(".txt")


def elegir(documentos, leer, semilla=SEMILLA, cuantos=CUANTOS):
    """Rutas de `cuantos` documentos de Ribera de 1 a 3 páginas, repartidos por tipo."""
    por_tipo = {}
    for d in sorted((d for d in documentos if d["empresa"] == "ribera"), key=lambda d: d["ruta"]):
        if 1 <= len(paginas(leer(d["ruta"]))) <= 3:
            por_tipo.setdefault(tipo(d["ruta"]), []).append(d["ruta"])
    rng = random.Random(semilla)
    for tipo_ in sorted(por_tipo):
        rng.shuffle(por_tipo[tipo_])
    elegidos = []
    while len(elegidos) < cuantos and any(por_tipo.values()):
        for tipo_ in sorted(por_tipo):
            if por_tipo[tipo_] and len(elegidos) < cuantos:
                elegidos.append(por_tipo[tipo_].pop())
    return elegidos


def nombre_pdf(ruta):
    return ruta.removesuffix(".txt").replace("/", "__") + ".pdf"


def pagina_png(texto, png, rng):
    """Texto -> PNG A4 200 ppp en gris, con rotación de hasta 1 grado y ruido."""
    fuente = png.with_suffix(".txt")
    fuente.write_text(texto, encoding="utf-8")
    base = [MAGICK, "-background", "white", "-fill", "black", "-font", "Helvetica", "-size", "1450x"]
    for tamano in (26, 23, 20, 17):
        orden = base + ["-pointsize", str(tamano), f"caption:@{fuente}", "-bordercolor", "white", "-border", "100"]
        alto = subprocess.run(orden + ["-format", "%h", "info:"], capture_output=True, text=True, check=True).stdout
        if int(alto) <= A4[1]:
            break
    subprocess.run(orden + ["-rotate", str(round(rng.uniform(-1, 1), 2)), "-gravity", "north", "-background", "white",
                            "-extent", f"{A4[0]}x{A4[1]}", "-colorspace", "Gray", "+noise", "Gaussian", "-attenuate",
                            "0.25", "-units", "PixelsPerInch", "-density", "200", str(png)], check=True)
    fuente.unlink()


def fabricar(texto, pdf, rng):
    with tempfile.TemporaryDirectory() as tmp:
        pngs = []
        for n, pagina in enumerate(paginas(texto), 1):
            pngs.append(Path(tmp) / f"p{n}.png")
            pagina_png(pagina, pngs[-1], rng)
        parcial = pdf.with_suffix(".parcial")
        subprocess.run([MAGICK, *map(str, pngs), "-units", "PixelsPerInch", "-density", "200", "-compress", "jpeg",
                        "-quality", "85", f"pdf:{parcial}"], check=True)
        parcial.rename(pdf)


def preparar(rutas, leer, carpeta):
    """Ruta del documento -> su PDF escaneado en `carpeta` (se reutilizan los que ya están)."""
    carpeta.mkdir(parents=True, exist_ok=True)
    rng = random.Random(SEMILLA)
    pdfs = {}
    for ruta in rutas:
        pdf = carpeta / nombre_pdf(ruta)
        if not (pdf.exists() and pdf.stat().st_size > 0):
            fabricar(leer(ruta), pdf, rng)
        pdfs[ruta] = pdf
    return pdfs
