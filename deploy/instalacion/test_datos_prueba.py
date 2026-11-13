import json
import os
import stat
import tempfile
import unittest
from pathlib import Path

import datos_prueba as dp
import escaneos


def seleccion():
    return dp.seleccionar(dp.leer_json(dp.ARCHIVO / "manifiesto.json"), dp.leer_json(dp.ARCHIVO / "claves.json"))


class SeleccionTest(unittest.TestCase):

    def test_tenComunidadesEnDosEmpresasYElProyectoAbierto(self):
        s = seleccion()
        cerrados = [p for p in s.proyectos if not p.get("abierto")]
        self.assertEqual(10, len(cerrados))
        self.assertEqual({"ribera", "albar"}, {p["empresa"] for p in cerrados})
        self.assertEqual(["procedimientos-generales"], [p["clave"] for p in s.proyectos if p.get("abierto")])

    def test_documentosVariadosDelOrdenDe150(self):
        s = seleccion()
        self.assertTrue(140 <= len(s.documentos) <= 170, len(s.documentos))
        nombres = [d["ruta"].split("/")[-1] for d in s.documentos]
        for tipo in ("caso-", "acta-", "poliza-", "anexo-", "contrato-", "presupuesto-", "norma-"):
            self.assertTrue(any(n.startswith(tipo) for n in nombres), tipo)
        self.assertTrue(any(d["proyecto"] is None for d in s.documentos))

    def test_cadaSustitucionTieneSusDosDocumentosCargados(self):
        s = seleccion()
        rutas = {d["ruta"] for d in s.documentos}
        self.assertGreaterEqual(len(s.parejas), 5)
        for viejo, vigente in s.parejas:
            self.assertIn(viejo, rutas)
            self.assertIn(vigente, rutas)

    def test_unasTreintaFichasEnLosTresEstados(self):
        s = seleccion()
        self.assertTrue(30 <= len(s.fichas) <= 40, len(s.fichas))
        self.assertEqual({"APPROVED", "PENDING", "REJECTED"}, {f["estado"] for f in s.fichas})

    def test_cadaFichaLaEscribeUnTrabajadorQueVeSuProyecto(self):
        s = seleccion()
        for f in s.fichas:
            autor = dp.autor_de(f, s)
            self.assertIn(f["proyecto"], dp.proyectos_de(autor, s), f["id"])


def completa():
    return dp.seleccionar_completo(dp.leer_json(dp.ARCHIVO / "manifiesto.json"), dp.leer_json(dp.ARCHIVO / "claves.json"))


class CompletaTest(unittest.TestCase):

    def test_cargaTodoElArchivo(self):
        s = completa()
        self.assertEqual((2, 94, 1236, 300), (len(s.empresas), len(s.proyectos), len(s.documentos), len(s.fichas)))
        self.assertEqual(55, len(s.parejas))
        self.assertTrue(s.completo)

    def test_ochoTrabajadoresConSusProyectos(self):
        s = completa()
        por_origen = {t["origen"]: t for t in s.trabajadores}
        self.assertEqual(8, len(s.trabajadores))
        self.assertEqual(10, len(dp.proyectos_de(por_origen["t02"], s)))
        self.assertEqual(61, len(dp.proyectos_de(por_origen["t01"], s)))
        self.assertEqual(33, len(dp.proyectos_de(por_origen["t06"], s)))
        self.assertEqual("raul", next(t["clave"] for t in s.trabajadores if t["origen"] == "t04"))

    def test_elAutorDeCadaFichaEsElDelManifiesto(self):
        s = completa()
        for f in s.fichas:
            self.assertEqual(f["autor"], dp.autor_de(f, s)["origen"])
            self.assertIn(f["proyecto"], dp.proyectos_de(dp.autor_de(f, s), s) + ["procedimientos-generales"])

    def test_cadaTipoDeCasoTieneTemas(self):
        for d in completa().documentos:
            if dp.es_caso(d["ruta"]):
                self.assertTrue(dp.temas_de(d["ruta"]), d["ruta"])

    def test_elModoPequenoNoCambia(self):
        s = seleccion()
        self.assertFalse(s.completo)
        self.assertEqual(["marta", "javier", "ignacio"], [t["clave"] for t in s.trabajadores])


class EscaneosTest(unittest.TestCase):

    def test_treintaDeRiberaDeUnaATresPaginasYDeterministas(self):
        docs = completa().documentos
        a = escaneos.elegir(docs, dp.leer_corpus)
        self.assertEqual(a, escaneos.elegir(docs, dp.leer_corpus))
        self.assertEqual(30, len(set(a)))
        self.assertTrue(all(r.startswith("ribera/") for r in a))
        self.assertTrue(all(1 <= len(escaneos.paginas(dp.leer_corpus(r))) <= 3 for r in a))
        self.assertGreaterEqual(len({escaneos.tipo(r) for r in a}), 8)


class TrabajadoresTest(unittest.TestCase):

    def test_javierSoloVeDosComunidadesDeRibera(self):
        s = seleccion()
        javier = next(t for t in s.trabajadores if t["clave"] == "javier")
        self.assertEqual(["c1-calle-pino-26", "c1-calle-roble-46"], sorted(dp.proyectos_de(javier, s)))

    def test_martaVeTodaRiberaEIgnacioTodaAlbar(self):
        s = seleccion()
        por_clave = {t["clave"]: t for t in s.trabajadores}
        ribera = {p["clave"] for p in s.proyectos if p["empresa"] == "ribera"}
        self.assertEqual(ribera, set(dp.proyectos_de(por_clave["marta"], s)))
        self.assertEqual("albar", por_clave["ignacio"]["empresa"])

    def test_nombresDelArchivo(self):
        s = seleccion()
        marta = next(t for t in s.trabajadores if t["clave"] == "marta")
        self.assertEqual(("Marta", "Olivares Ríos"), (marta["nombre"], marta["apellidos"]))


class TemasTest(unittest.TestCase):

    def test_cadaCasoCargadoTieneTemasDeLaLista(self):
        nombres = {t["name"] for t in dp.leer_json(dp.TEMAS)}
        for d in seleccion().documentos:
            if dp.es_caso(d["ruta"]):
                temas = dp.temas_de(d["ruta"])
                self.assertTrue(temas, d["ruta"])
                self.assertTrue(set(temas) <= nombres, d["ruta"])

    def test_loQueNoEsCasoNoLlevaTemas(self):
        self.assertEqual([], dp.temas_de("ribera/c1-olmo14/poliza-seguro-2023.txt"))

    def test_unTipoDeCasoDesconocidoNoPasaEnSilencio(self):
        with self.assertRaises(KeyError):
            dp.temas_de("ribera/c1-olmo14/caso-2025-001-algo-nuevo.txt")


class CredencialesTest(unittest.TestCase):

    def test_ficherosSoloLegiblesPorSuDueno(self):
        with tempfile.TemporaryDirectory() as tmp:
            carpeta = Path(tmp) / "datos-prueba"
            datos = {"api": "http://127.0.0.1:8080", "panel": "http://127.0.0.1:8088",
                     "administrador": {"nombre": "Admin", "email": "a@x.invalid", "password": "secreta-123456"},
                     "trabajadores": {"marta": {"nombre": "Marta Olivares Ríos", "empresa": "Administraciones Ribera",
                                                "proyectos": ["Calle Olmo 14"], "clave": "odin_abc"}}}
            dp.escribir_credenciales(carpeta, datos)
            self.assertEqual(0o700, stat.S_IMODE(os.stat(carpeta).st_mode))
            for nombre in ("credenciales.local.md", "credenciales.local.json"):
                self.assertEqual(0o600, stat.S_IMODE(os.stat(carpeta / nombre).st_mode), nombre)
            self.assertEqual(datos, json.loads((carpeta / "credenciales.local.json").read_text()))
            md = (carpeta / "credenciales.local.md").read_text()
            self.assertIn("Marta Olivares Ríos", md)
            self.assertIn("odin_abc", md)


class MultipartTest(unittest.TestCase):

    def test_cuerpoConElFichero(self):
        cuerpo, tipo = dp.multipart("acta.txt", "Acta de la junta".encode())
        self.assertTrue(tipo.startswith("multipart/form-data; boundary="))
        self.assertIn(b'name="file"; filename="acta.txt"', cuerpo)
        self.assertIn("Acta de la junta".encode(), cuerpo)


if __name__ == "__main__":
    unittest.main()
