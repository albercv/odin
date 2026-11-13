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

    def test_los_proyectos_se_llaman_sin_forma_juridica(self):
        nombres = [p["nombre"] for p in self.manifiesto["proyectos"]]
        self.assertEqual(nombres, ["Talleres Murcano", "Conservas Dalmira", "Logística Penedal",
                                   "Alojamientos Cerrolago", "Bodegas Quintafría", "Cerámicas Valtierno"])

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

    def test_los_26_documentos_y_los_json_anteriores_no_cambian(self):
        for rel in ficheros(self.datos / "corpus"):
            if rel.parts[1] == "ceramicas-valtierno":
                continue
            committed = AQUI / "datos" / "corpus" / rel
            self.assertEqual((self.datos / "corpus" / rel).read_bytes(), committed.read_bytes(), str(rel))
        for nombre in ("bateria.json", "claves.json"):
            self.assertEqual((self.datos / nombre).read_bytes(), (AQUI / "datos" / nombre).read_bytes(), nombre)


# ---------- expediente grande (D-078) ----------
EXP_PROY = "ceramicas-valtierno"
EXP_DIR = "brumeral/ceramicas-valtierno/"
CASO = EXP_DIR + "caso-2025-017-ampliacion-horno.txt"
DECOY = "brumeral/logistica-penedal/correo-ref-017.txt"
PRES2 = EXP_DIR + "expte-2025-017-presupuesto-2.txt"
PRES2R = EXP_DIR + "expte-2025-017-presupuesto-2-revisado.txt"
PREFIJOS = {"contrato-ingenieria": 1, "adenda-1": 1, "presupuesto-": 7, "factura-": 6, "correo-": 25,
            "acta-obra-": 8, "informe-tecnico-": 8, "certificado-": 4}
FIJOS = {  # cadena literal -> único documento del expediente que puede llevarla
    "48.600": "expte-2025-017-contrato-ingenieria.txt",
    "Directora facultativa: Irene Salcedo Moya.": "expte-2025-017-contrato-ingenieria.txt",
    "Plazo de pago: 60 días": "expte-2025-017-contrato-ingenieria.txt",
    "52.200": "expte-2025-017-adenda-1.txt",
    "35.400": "expte-2025-017-presupuesto-2.txt",
    "33.900": "expte-2025-017-presupuesto-2-revisado.txt",
    "1.180": "expte-2025-017-informe-tecnico-3.txt",
    "12 de septiembre": "expte-2025-017-acta-obra-5.txt",
}
GX_SUBTIPOS = {"GX01": "concreta", "GX02": "concreta", "GX03": "concreta", "GX04": "sustituidos", "GX05": "concreta",
               "GX06": "concreta", "GX07": "analisis", "GX08": "analisis", "GX09": "analisis", "GX10": "contiene",
               "GX11": "no_consta", "GX12": "ajenos", "GX13": "aislamiento", "GX14": "aislamiento"}


class ExpedienteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.datos = Path(cls.tmp.name) / "a"
        generar.generar(cls.datos)  # las autocomprobaciones del expediente también se ejecutan aquí
        cls.exp = json.loads((cls.datos / "expedientes.json").read_text(encoding="utf-8"))
        cls.gx = json.loads((cls.datos / "bateria-expediente.json").read_text(encoding="utf-8"))
        cls.manifiesto = json.loads((cls.datos / "manifiesto.json").read_text(encoding="utf-8"))

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def texto(self, ruta):
        return (self.datos / "expedientes" / ruta).read_text(encoding="utf-8")

    def docs(self):
        return self.exp[0]["documentos"]

    def test_un_expediente_con_60_documentos_que_existen(self):
        self.assertEqual(len(self.exp), 1)
        e = self.exp[0]
        self.assertEqual(e["caso"], CASO)
        self.assertEqual(e["proyecto"], EXP_PROY)
        self.assertEqual(e["compartidos"], [])
        self.assertEqual(len(self.docs()), 60)
        self.assertEqual(len(set(self.docs())), 60)
        for r in self.docs():
            self.assertTrue(r.startswith(EXP_DIR), r)
            self.assertTrue((self.datos / "expedientes" / r).is_file(), r)

    def test_mezcla_de_tipos_del_cuadro(self):
        nombres = [r.rsplit("/", 1)[1] for r in self.docs()]
        for prefijo, n in PREFIJOS.items():
            cuenta = sum(1 for x in nombres if x.startswith("expte-2025-017-" + prefijo))
            self.assertEqual(cuenta, n, prefijo)

    def test_sustituciones_formas_y_decoy(self):
        e = self.exp[0]
        self.assertEqual(e["sustituciones"], [[PRES2, PRES2R]])
        self.assertEqual(e["formas_numero"], ["expte. 2025/017", "exp. 017/25", "su referencia 017", "expediente nº 17"])
        self.assertEqual(e["ajenos"], [DECOY])
        self.assertNotIn(DECOY, self.docs())
        self.assertTrue((self.datos / "expedientes" / DECOY).is_file())
        self.assertNotIn(DECOY, [d["ruta"] for d in self.manifiesto["documentos"]])

    def test_cada_documento_es_una_sola_pagina_con_10_a_25_lineas(self):
        for r in self.docs() + [DECOY]:
            texto = self.texto(r)
            self.assertTrue(texto.startswith("=== Página 1 ===\n"), r)
            self.assertEqual(len(re.findall(r"^=== Página \d+ ===$", texto, re.MULTILINE)), 1, r)
            lineas = [x for x in texto.split("\n")[1:] if x.strip()]
            self.assertTrue(10 <= len(lineas) <= 25, f"{r}: {len(lineas)} líneas")

    def test_sin_puntos_dobles_en_el_expediente(self):
        for r in self.docs() + [DECOY]:
            self.assertIsNone(re.search(r"\.\.|S\.[LA]\.\.", self.texto(r)), r)

    def test_ningun_expediente_usa_las_palabras_prohibidas(self):
        for r in self.docs() + [DECOY]:
            self.assertIsNone(REDACCION.search(sin_tildes(self.texto(r))), r)

    def test_hechos_fijos_solo_en_su_documento(self):
        for cadena, fichero in FIJOS.items():
            encontrados = [r.rsplit("/", 1)[1] for r in self.docs() + [DECOY] if cadena in self.texto(r)]
            self.assertEqual(encontrados, [fichero], cadena)

    def test_los_totales_de_factura_solo_estan_en_las_facturas(self):
        for r in self.docs() + [DECOY]:
            es_factura = "expte-2025-017-factura-" in r
            for cadena in ("10.527,00", "1.827,00", "63.162"):
                self.assertEqual(cadena in self.texto(r), es_factura and cadena != "63.162", (r, cadena))

    def test_nada_de_penalizacion_por_retraso(self):
        for r in self.docs() + [DECOY]:
            self.assertNotIn("penalización", self.texto(r).lower(), r)

    def test_las_seis_facturas_suman_52200_y_63162(self):
        facturas = [r for r in self.docs() if "expte-2025-017-factura-" in r]
        self.assertEqual(len(facturas), 6)
        base = total = 0
        numeros = set()
        for r in facturas:
            texto = self.texto(r)
            base += int(re.search(r"Base imponible: (\d{1,3}(?:\.\d{3})*),\d{2} euros", texto).group(1).replace(".", ""))
            total += int(re.search(r"Total factura: (\d{1,3}(?:\.\d{3})*),\d{2} euros", texto).group(1).replace(".", ""))
            numeros.add(re.search(r"Número de factura: (F-2025-\d{3})", texto).group(1))
        self.assertEqual((base, total), (52200, 63162))
        self.assertEqual(len(numeros), 6)

    def test_caso_y_proyecto_en_el_manifiesto(self):
        proyectos = {p["clave"]: p for p in self.manifiesto["proyectos"]}
        self.assertEqual(proyectos[EXP_PROY], {"clave": EXP_PROY, "empresa": "brumeral", "nombre": "Cerámicas Valtierno", "abierto": False})
        docs = {d["ruta"]: d for d in self.manifiesto["documentos"]}
        self.assertEqual(docs[CASO]["titulo"], "Caso 2025-017, ampliación de la línea del horno 2")
        self.assertEqual(docs[CASO]["proyecto"], EXP_PROY)
        texto = (self.datos / "corpus" / CASO).read_text(encoding="utf-8")
        self.assertIn("FICHA DE CASO", texto)
        self.assertTrue(texto.startswith("=== Página 1 ==="))

    def test_t10_no_ve_el_proyecto(self):
        trab = {t["clave"]: t for t in self.manifiesto["trabajadores"]}
        self.assertEqual(trab["t10"]["proyectos"], ["talleres-murcano", "conservas-dalmira", "bodegas-quintafria"])
        self.assertEqual(trab["t09"]["proyectos"], ["*"])
        self.assertEqual(trab["t11"]["proyectos"], ["*"])

    def test_catorce_preguntas_gx_con_sus_subtipos(self):
        self.assertEqual([q["id"] for q in self.gx], [f"GX{n:02d}" for n in range(1, 15)])
        for q in self.gx:
            self.assertEqual((q["tipo"], q["origen"]), ("expediente", "generalidad"), q["id"])
            self.assertEqual(q["subtipo"], GX_SUBTIPOS[q["id"]], q["id"])
            self.assertEqual(q["caso"], CASO, q["id"])
            self.assertIn("esperados", q)
            self.assertIn("concreta", q)

    def test_concreta_solo_donde_toca(self):
        concretas = {q["id"] for q in self.gx if q["concreta"]}
        self.assertEqual(concretas, {"GX01", "GX02", "GX03", "GX04", "GX05", "GX06"})

    def test_rol_neutro_y_no_afirmar_en_t09_y_t10(self):
        for q in self.gx:
            if q["actor"] in ("t09", "t10"):
                self.assertEqual(q.get("rol"), "neutro", q["id"])
                if not q["abstener"]:
                    self.assertEqual(q.get("no_afirmar"), ["comunidad", "propietario", "finca", "vecino", "junta de propietarios", "derrama"], q["id"])

    def test_ausente_solo_en_no_consta(self):
        for q in self.gx:
            if q["subtipo"] == "no_consta":
                self.assertEqual(q["ausente"], ["penalización"])
                self.assertTrue(q["abstener"])
            else:
                self.assertNotIn("ausente", q, q["id"])

    def test_gx07_pide_las_seis_facturas_sin_iva(self):
        q = next(x for x in self.gx if x["id"] == "GX07")
        self.assertIn("sin IVA", q["pregunta"])
        self.assertTrue(q["todas_las_citas"])
        self.assertEqual(len(q["esperados"]), 6)
        self.assertTrue(all("expte-2025-017-factura-" in r for r in q["esperados"]))
        self.assertEqual(q["claves"], [["52.200"]])

    def test_gx04_y_gx09_marcan_el_presupuesto_sustituido(self):
        por_id = {q["id"]: q for q in self.gx}
        self.assertEqual(por_id["GX04"]["sustituidos"], [PRES2])
        self.assertEqual(por_id["GX04"]["prohibidas"], ["35.400"])
        self.assertEqual(por_id["GX04"]["claves"], [["33.900"]])
        claves = por_id["GX09"]["claves"]
        self.assertEqual(sorted(g[0] for g in claves[:6]), sorted([
            "Refractarios Sierra del Mar", "Montajes Garbea", "Electroindustrial Prado Alto",
            "Aislamientos Térmicos Olmedo", "Transportes Hidalgo Cruz", "Ingeniería Civil Ribazo"]))
        self.assertEqual(sorted(g[0] for g in claves[6:]), sorted(
            ["21.750", "33.900", "27.480", "12.960", "6.240", "41.300"]))
        self.assertIn(["33.900"], claves)
        self.assertNotIn(["35.400"], claves)

    def test_gx12_lista_los_documentos_con_su_referencia_017(self):
        q = next(x for x in self.gx if x["id"] == "GX12")
        self.assertEqual(q["docs_prohibidos"], [DECOY])
        self.assertEqual(q["subtipo"], "ajenos")
        self.assertIn("su referencia 017", q["pregunta"])
        con_forma = sorted(r for r in self.docs() if "su referencia 017" in self.texto(r))
        self.assertEqual(len(con_forma), 9)
        self.assertEqual(sorted(q["esperados"]), con_forma)
        self.assertNotIn(DECOY, q["esperados"])
        self.assertTrue(q["todas_las_citas"])
        self.assertEqual(len(q["citas_resueltas"]), 2)
        for c in q["citas_resueltas"]:
            self.assertIn(c["doc"], con_forma)
            self.assertEqual(c["paginas"], [1])
            self.assertIn(c["ancla"], self.texto(c["doc"]).split("=== Página 2 ===")[0])
        self.assertEqual(q["claves"], [])

    def test_gx13_y_gx14_son_de_aislamiento_y_se_abstienen(self):
        por_id = {q["id"]: q for q in self.gx}
        self.assertEqual(por_id["GX13"]["actor"], "t10")
        self.assertEqual(por_id["GX13"]["docs_prohibidos"], [EXP_DIR])
        self.assertEqual(por_id["GX14"]["actor"], "t01")
        self.assertEqual(por_id["GX14"]["docs_prohibidos"], ["brumeral/"])
        for id_ in ("GX13", "GX14"):
            self.assertTrue(por_id[id_]["abstener"])

    def test_ids_del_expediente_no_chocan_con_las_gen(self):
        bateria = json.loads((self.datos / "bateria.json").read_text(encoding="utf-8"))
        self.assertEqual(len(bateria), 32)
        self.assertFalse({q["id"] for q in bateria} & {q["id"] for q in self.gx})


if __name__ == "__main__":
    unittest.main()
