"""Pruebas de la capa «generalidad» (Brumeral): determinismo, tipos, redacción y rol neutro."""
import json
import re
import sys
import tempfile
import unittest
import unicodedata
from collections import Counter
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import generar  # noqa: E402

TIPOS_ESPERADOS = {
    "generica": 4, "dato_concreto": 5, "version": 5, "identificador": 1, "vocabulario": 2,
    "contradiccion": 3, "lista_completa": 2, "ficha": 1, "sin_fuente": 5, "aislamiento": 4,
}
REDACCION = re.compile(r"\b(comunidad|propietario|vecino|finca|junta|derrama)")


def sin_tildes(texto):
    descompuesto = unicodedata.normalize("NFD", texto.lower())
    return "".join(c for c in descompuesto if unicodedata.category(c) != "Mn")


def ficheros(raiz):
    return sorted(p.relative_to(raiz) for p in raiz.rglob("*") if p.is_file())


class GeneralidadTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.raiz = Path(cls.tmp.name)
        cls.datos = cls.raiz / "a"
        generar.generar(cls.datos)  # las autocomprobaciones se ejecutan aquí y lanzan si fallan
        cls.bateria = json.loads((cls.datos / "bateria.json").read_text(encoding="utf-8"))
        cls.manifiesto = json.loads((cls.datos / "manifiesto.json").read_text(encoding="utf-8"))

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_dos_ejecuciones_dan_los_mismos_ficheros(self):
        otro = self.raiz / "b"
        generar.generar(otro)
        self.assertEqual(ficheros(self.datos), ficheros(otro))
        for rel in ficheros(self.datos):
            self.assertEqual((self.datos / rel).read_bytes(), (otro / rel).read_bytes(), str(rel))

    def test_32_preguntas_con_los_tipos_del_cuadro(self):
        ids = [q["id"] for q in self.bateria]
        self.assertEqual(ids, [f"GEN{n:02d}" for n in range(1, 33)])
        self.assertEqual(dict(Counter(q["tipo"] for q in self.bateria)), TIPOS_ESPERADOS)

    def test_t09_y_t10_llevan_rol_neutro(self):
        for q in self.bateria:
            if q["actor"] in ("t09", "t10"):
                self.assertEqual(q.get("rol"), "neutro", q["id"])

    def test_no_afirmar_lleva_la_lista_acordada(self):
        esperada = ["comunidad", "propietario", "finca", "vecino", "junta de propietarios", "derrama"]
        for q in self.bateria:
            if q["actor"] in ("t09", "t10") and not q["abstener"]:
                self.assertEqual(q.get("no_afirmar"), esperada, q["id"])

    def test_ningun_documento_ni_ficha_usa_las_palabras_prohibidas(self):
        for rel in ficheros(self.datos / "corpus"):
            texto = sin_tildes((self.datos / "corpus" / rel).read_text(encoding="utf-8"))
            self.assertIsNone(REDACCION.search(texto), str(rel))
        for ficha in self.manifiesto["fichas"]:
            self.assertIsNone(REDACCION.search(sin_tildes(ficha["titulo"] + " " + ficha["texto"])), ficha["id"])

    def test_alternativas_de_claves_de_la_revision(self):
        esperadas = {
            "GEN06": [["29/06/2025", "29 de junio de 2025"]], "GEN09": [["72 euros"]],
            "GEN12": [["186.500", "27.900"]], "GEN13": [["45 días"]], "GEN16": [["15 de julio de 2025", "15/07/2025"]],
            "GEN17": [["6.760", "8.179,60"]], "GEN18": [["60 días"], ["30 días"]], "GEN19": [["10 semanas"], ["8 semanas"]],
            "GEN23": [["8:30", "08:30", "8.30", "8,30"]],
        }
        por_id = {q["id"]: q for q in self.bateria}
        for qid, claves in esperadas.items():
            self.assertEqual(por_id[qid]["claves"], claves, qid)

    def test_sin_puntos_dobles_tras_los_nombres(self):
        for rel in ficheros(self.datos / "corpus"):
            texto = (self.datos / "corpus" / rel).read_text(encoding="utf-8")
            self.assertIsNone(re.search(r"\.\.|S\.[LA]\.\.", texto), str(rel))

    def test_cada_pagina_tiene_entre_10_y_25_lineas(self):
        for doc in self.manifiesto["documentos"]:
            texto = (self.datos / "corpus" / doc["ruta"]).read_text(encoding="utf-8")
            for pagina in re.split(r"^=== Página \d+ ===$", texto, flags=re.MULTILINE)[1:]:
                lineas = [linea for linea in pagina.split("\n") if linea.strip()]
                self.assertTrue(10 <= len(lineas) <= 25, f"{doc['ruta']}: {len(lineas)} líneas")

    def test_cada_pagina_empieza_con_su_marcador(self):
        for doc in self.manifiesto["documentos"]:
            texto = (self.datos / "corpus" / doc["ruta"]).read_text(encoding="utf-8")
            marcas = re.findall(r"^=== Página (\d+) ===$", texto, re.MULTILINE)
            self.assertTrue(texto.startswith("=== Página 1 ===\n"), doc["ruta"])
            self.assertEqual(marcas, [str(n) for n in range(1, len(marcas) + 1)], doc["ruta"])


if __name__ == "__main__":
    unittest.main()
