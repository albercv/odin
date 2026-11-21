import json
import os
import tempfile
import unittest
import urllib.parse
from pathlib import Path

import cargar_expedientes as C

CASO_A, CASO_B = "ribera/pa/caso-2024-001-x.txt", "ribera/pb/caso-2024-002-y.txt"
DOC = lambda c, n: f"ribera/{c}/expte-2024-{'001' if c == 'pa' else '002'}-{n}.txt"
COMPARTIDO, GENERAL, AJENO = "ribera/pa/expte-contrato-perito.txt", "ribera/_general/expte-protocolo.txt", "ribera/pc/correo-ajeno-ref-001.txt"
MANIFIESTO = [
    {"caso": CASO_A, "proyecto": "pa", "documentos": [DOC("pa", "informe-perito"), DOC("pa", "factura")],
     "compartidos": [COMPARTIDO, GENERAL], "ajenos": [AJENO], "formas_numero": ["a"], "sustituciones": [[DOC("pa", "factura"), DOC("pa", "informe-perito")]]},
    {"caso": CASO_B, "proyecto": "pb", "documentos": [DOC("pb", "informe-perito")], "compartidos": [GENERAL], "ajenos": [], "formas_numero": ["b"]},
]


class Falso:
    """Misma forma que Api.admin de datos_prueba.py."""
    def __init__(self, casos, retardo=0, falla=None):
        self.docs, self.llamadas, self.n, self.retardo, self.falla = dict(casos), [], 0, retardo, falla
        self.asignados, self.polls = set(), {}

    def admin(self, metodo, ruta, cuerpo=None, binario=None):
        self.llamadas.append((metodo, ruta, cuerpo, binario))
        if self.falla and self.falla in ruta:
            raise RuntimeError("HTTP 409 simulado")
        if metodo == "GET":
            i = ruta.rsplit("/", 1)[1]
            d = self.docs[i]
            if i.startswith("n") and d["status"] != "READY":
                self.polls[i] = self.polls.get(i, 0) + 1
                if self.polls[i] > self.retardo:
                    d["status"] = "READY"
            return dict(d)
        if ruta.endswith("/documents/case-marks"):
            for i in cuerpo["documentIds"]:
                self.docs[i]["caseDocument"] = True
            return {"marked": len(cuerpo["documentIds"])}
        if ruta.endswith("/file/documents"):
            nuevos = {(ruta, i) for i in cuerpo["documentIds"]} - self.asignados
            self.asignados |= nuevos
            return {"added": len(nuevos)}
        if metodo == "PUT":
            self.docs[ruta.split("/")[4]]["supersededBy"] = {"id": cuerpo["replacementId"]}
            return {}
        self.n += 1
        q = urllib.parse.parse_qs(ruta.split("?")[1])
        self.docs[f"n{self.n}"] = {"id": f"n{self.n}", "projectId": q.get("projectId", [None])[0], "status": "RECEIVED", "caseDocument": False}
        return {"document": {"id": f"n{self.n}"}, "duplicate": False}

    def rutas(self, metodo):
        return [r for m, r, *_ in self.llamadas if m == metodo]


def vista(i, proyecto, estado="READY", caso=True):  # el caso ya marcado salvo que se pida lo contrario
    return {"id": i, "companyId": "emp", "projectId": proyecto, "status": estado, "caseDocument": caso}


class CargarExpedientes(unittest.TestCase):
    def setUp(self):
        self.t = tempfile.TemporaryDirectory()
        self.raiz = Path(self.t.name)
        for e in MANIFIESTO:
            for r in e["documentos"] + e["compartidos"] + e["ajenos"]:
                f = self.raiz / "expedientes" / r
                f.parent.mkdir(parents=True, exist_ok=True)
                f.write_text("=== Página 1 ===\ntexto\n", encoding="utf-8")

    def tearDown(self):
        self.t.cleanup()

    def cargar(self, mapa, falso, **kw):
        return C.cargar_expedientes(falso, mapa, MANIFIESTO, self.raiz, **({"intervalo": 0} | kw))

    def test_solo_el_caso_cargado_y_se_cuentan_omitidos(self):
        falso = Falso({"A": vista("A", "PA"), "X": vista("X", "PC")})
        r = self.cargar({CASO_A: "A", "ribera/pc/otro.txt": "X"}, falso)
        self.assertEqual((r["subidos"], r["omitidos"]), (5, 1))
        self.assertEqual(r["omitidos_casos"], [{"caso": CASO_B, "motivo": "el documento del caso no está cargado"}])
        self.assertEqual(sum(1 for _, ruta, *_ in falso.llamadas if "/file/documents" in ruta), 1)

    def test_asigna_propios_y_compartidos_pero_no_ajenos(self):
        falso = Falso({"A": vista("A", "PA"), "X": vista("X", "PC")})
        mapa = {CASO_A: "A", "ribera/pc/otro.txt": "X"}
        r = self.cargar(mapa, falso)
        asignado = next(c for m, ruta, c, b in falso.llamadas if ruta.endswith("/cases/A/file/documents"))["documentIds"]
        self.assertEqual(len(asignado), 4)
        self.assertNotIn(mapa[AJENO], asignado)
        self.assertEqual(r["añadidos"], 4)

    def test_ajeno_solo_si_su_proyecto_esta_cargado(self):
        falso = Falso({"A": vista("A", "PA")})
        r = self.cargar({CASO_A: "A"}, falso)
        self.assertEqual(r["subidos"], 4)

    def test_caso_no_listo_se_omite_con_motivo(self):
        falso = Falso({"A": vista("A", "PA", "PENDING_READER")})
        r = self.cargar({CASO_A: "A"}, falso)
        self.assertEqual((r["subidos"], r["añadidos"], r["omitidos"]), (0, 0, 2))
        self.assertIn("PENDING_READER", r["omitidos_casos"][0]["motivo"])

    def test_compartido_se_sube_una_vez_y_el_mapa_se_completa(self):
        falso = Falso({"A": vista("A", "PA"), "B": vista("B", "PB")})
        mapa = {CASO_A: "A", CASO_B: "B"}
        self.cargar(mapa, falso)
        self.assertEqual(sum(1 for m, r, *_ in falso.llamadas if m == "POST" and "title=" in r and "protocolo" in urllib.parse.unquote(r)), 1)
        self.assertIn(GENERAL, mapa)

    def test_repetir_no_vuelve_a_subir(self):
        falso = Falso({"A": vista("A", "PA")})
        mapa = {CASO_A: "A"}
        self.cargar(mapa, falso)
        antes = falso.n
        self.cargar(mapa, falso)
        self.assertEqual(falso.n, antes)

    def test_sin_marcar_casos_un_documento_que_no_es_caso_se_omite(self):
        falso = Falso({"A": vista("A", "PA", caso=False)})
        r = self.cargar({CASO_A: "A"}, falso)
        self.assertEqual(r["omitidos_casos"], [{"caso": CASO_A, "motivo": "no es caso"}, {"caso": CASO_B, "motivo": C.MOTIVO_SIN_CASO}])
        self.assertEqual(r["subidos"], 0)

    def test_con_marcar_casos_lo_marca_y_sustituye(self):
        falso = Falso({"A": vista("A", "PA", caso=False)})
        r = self.cargar({CASO_A: "A"}, falso, marcar_casos=True)
        self.assertEqual((r["marcados"], r["sustituidos"]), (1, 1))
        self.assertEqual(len(falso.rutas("PUT")), 1)

    def test_segunda_pasada_no_anade_ni_sustituye_de_nuevo(self):
        falso = Falso({"A": vista("A", "PA")})
        mapa = {CASO_A: "A"}
        self.cargar(mapa, falso)
        r = self.cargar(mapa, falso)
        self.assertEqual((r["subidos"], r["añadidos"], r["sustituidos"]), (0, 0, 0))
        self.assertEqual(len(falso.rutas("PUT")), 1)

    def test_espera_a_que_esten_listos_antes_de_sustituir(self):
        falso = Falso({"A": vista("A", "PA")}, retardo=3)
        r = self.cargar({CASO_A: "A"}, falso)
        self.assertEqual((r["sustituidos"], r["pendientes"]), (1, []))
        self.assertGreaterEqual(max(falso.polls.values()), 3)

    def test_si_no_llegan_a_listos_la_sustitucion_queda_pendiente(self):
        falso = Falso({"A": vista("A", "PA")}, retardo=10**6)
        r = self.cargar({CASO_A: "A"}, falso, espera=0)
        self.assertEqual(r["sustituidos"], 0)
        self.assertEqual(len(r["pendientes"]), 1)
        self.assertEqual(r["añadidos"], 4)

    def test_un_fallo_en_un_caso_no_aborta_la_carga(self):
        falso = Falso({"A": vista("A", "PA"), "B": vista("B", "PB")}, falla="/cases/A/")
        r = self.cargar({CASO_A: "A", CASO_B: "B"}, falso)
        self.assertEqual(len(r["errores"]), 1)
        self.assertEqual(r["errores"][0]["caso"], CASO_A)
        self.assertGreater(r["añadidos"], 0)

    def test_guarda_el_mapa_tras_cada_caso(self):
        falso = Falso({"A": vista("A", "PA"), "B": vista("B", "PB")})
        vistos = []
        self.cargar({CASO_A: "A", CASO_B: "B"}, falso, guardar=lambda m: vistos.append(len(m)))
        self.assertEqual(len(vistos), 2)
        self.assertLess(vistos[0], vistos[1])

    def test_subida_va_como_multipart_al_proyecto_del_caso(self):
        falso = Falso({"A": vista("A", "PA")})
        self.cargar({CASO_A: "A"}, falso)
        m, ruta, cuerpo, binario = next(l for l in falso.llamadas if l[0] == "POST" and "title=" in l[1])
        self.assertIn("/v1/admin/companies/emp/documents?", ruta)
        self.assertIn("projectId=PA", ruta)
        self.assertTrue(binario[1].startswith("multipart/form-data; boundary="))

    def test_guardar_es_atomico_y_privado(self):
        with tempfile.TemporaryDirectory() as d:
            ruta = Path(d) / "carga.json"
            C.guardar_json(ruta, {"a": 1})
            self.assertEqual(oct(os.stat(ruta).st_mode & 0o777), "0o600")
            self.assertEqual(os.listdir(d), ["carga.json"])
            with self.assertRaises(TypeError):
                C.guardar_json(ruta, {"a": object()})
            self.assertEqual(json.loads(ruta.read_text()), {"a": 1})
            self.assertEqual(os.listdir(d), ["carga.json"])


if __name__ == "__main__":
    unittest.main()
