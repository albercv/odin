import json
import os
import tempfile
import unittest
import unittest.mock
from pathlib import Path

import anadir_proyecto as ap

PROYECTO = {"clave": "mirador", "empresa": "ribera", "nombre": "Residencial Mirador", "abierto": False}


def documento(nombre):
    return {"ruta": f"ribera/mirador/{nombre}", "empresa": "ribera", "proyecto": "mirador", "titulo": nombre}


def ficha(estado, **extra):
    return {"id": "F1", "empresa": "ribera", "proyecto": "mirador", "autor": "t01", "estado": estado,
            "titulo": "Ficha", "texto": "Texto"} | extra


def manifiesto(**cambios):
    return {"empresas": [{"clave": "ribera", "nombre": "Administraciones Ribera", "codigo": "RIB"}],
            "proyectos": [PROYECTO], "trabajadores": [],
            "documentos": [documento("acta-2016.txt"), documento("caso-2025-001-filtracion.txt"),
                           documento("poliza-2016.pdf"), documento("poliza-2019.pdf")],
            "sustituciones": [{"viejo": "ribera/mirador/poliza-2016.pdf", "nuevo": "ribera/mirador/poliza-2019.pdf"}],
            "fichas": [ficha("APPROVED"), ficha("CORRECTED", correccion="Texto corregido"), ficha("PENDING")]} | cambios


EXPEDIENTE = {"caso": "ribera/mirador/caso-2025-001-filtracion.txt", "proyecto": "mirador",
              "documentos": ["ribera/mirador/expte-informe.txt"], "compartidos": [], "ajenos": []}


class Carpeta:
    """Carpeta de datos temporal con un fichero por documento del manifiesto y del expediente."""

    def __init__(self, m, expedientes=(EXPEDIENTE,)):
        self.tmp = tempfile.TemporaryDirectory()
        self.ruta = Path(self.tmp.name)
        for d in m["documentos"]:
            self.escribir("corpus", d["ruta"], "Acta de la junta del Residencial Mirador del Puerto, año 2016.")
        for e in expedientes:
            for r in e["documentos"] + e["compartidos"] + e["ajenos"]:
                self.escribir("expedientes", r, "Informe del perito.")

    def escribir(self, base, ruta, texto):
        fichero = self.ruta / base / ruta
        fichero.parent.mkdir(parents=True, exist_ok=True)
        fichero.write_text(texto, encoding="utf-8")

    def __enter__(self):
        return self.ruta

    def __exit__(self, *_):
        self.tmp.cleanup()


class ProblemasTest(unittest.TestCase):

    def problemas(self, m, expedientes=(EXPEDIENTE,), max_bytes=1000):
        with Carpeta(manifiesto()) as carpeta:
            return ap.problemas(m, list(expedientes), carpeta, max_bytes)

    def test_unaCargaBienFormadaNoTieneProblemas(self):
        self.assertEqual([], self.problemas(manifiesto()))

    def test_unSoloProyectoNuevo(self):
        otro = PROYECTO | {"clave": "otro", "nombre": "Otro"}
        self.assertIn("uno", self.problemas(manifiesto(proyectos=[PROYECTO, otro]))[0])

    def test_ningunDocumentoFueraDelProyecto(self):
        general = documento("norma.txt") | {"proyecto": None}
        problemas = self.problemas(manifiesto(documentos=manifiesto()["documentos"] + [general]))
        self.assertTrue(any("norma.txt" in p for p in problemas), problemas)

    def test_cadaDocumentoTieneSuFichero(self):
        problemas = self.problemas(manifiesto(documentos=manifiesto()["documentos"] + [documento("falta.txt")]))
        self.assertTrue(any("falta.txt" in p for p in problemas), problemas)

    def test_ningunFicheroPasaDelLimiteDeLaApi(self):
        self.assertTrue(self.problemas(manifiesto(), max_bytes=10))

    def test_unaFichaCorregidaLlevaElTextoCorregido(self):
        self.assertTrue(self.problemas(manifiesto(fichas=[ficha("CORRECTED")])))

    def test_unEstadoDeFichaDesconocidoNoPasa(self):
        self.assertTrue(self.problemas(manifiesto(fichas=[ficha("DUDOSA")])))

    def test_unaSustitucionSoloEntreDocumentosDelManifiesto(self):
        sustituciones = [{"viejo": "ribera/mirador/poliza-2016.pdf", "nuevo": "ribera/mirador/no-esta.pdf"}]
        self.assertTrue(self.problemas(manifiesto(sustituciones=sustituciones)))

    def test_elCasoDeUnExpedienteEsUnDocumentoDeCaso(self):
        self.assertTrue(self.problemas(manifiesto(), [EXPEDIENTE | {"caso": "ribera/mirador/acta-2016.txt"}]))

    def test_cadaDocumentoDeUnExpedienteTieneSuFichero(self):
        falta = EXPEDIENTE | {"documentos": ["ribera/mirador/expte-no-esta.txt"]}
        self.assertTrue(self.problemas(manifiesto(), [falta]))

    def test_haceFaltaUnTextoParaComprobarElAislamiento(self):
        self.assertTrue(self.problemas(manifiesto(documentos=[documento("poliza-2016.pdf"), documento("poliza-2019.pdf")]),
                                       expedientes=()))


class TipoTest(unittest.TestCase):

    def test_tipoPorLaExtension(self):
        esperado = {"a.csv": "text/csv", "a.md": "text/markdown", "a.txt": "text/plain", "a.pdf": "application/pdf",
                    "a.png": "image/png", "a.jpg": "image/jpeg",
                    "a.xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "a.xls": "application/vnd.ms-excel", "a.ods": "application/vnd.oasis.opendocument.spreadsheet"}
        self.assertEqual(esperado, {n: ap.tipo_de(Path(n)) for n in esperado})


class RevisionTest(unittest.TestCase):

    def test_cadaEstadoConSuRevision(self):
        self.assertEqual({"decision": "APPROVE"}, ap.revision(ficha("APPROVED")))
        self.assertEqual({"decision": "CORRECT", "correctedBody": "Otro"}, ap.revision(ficha("CORRECTED", correccion="Otro")))
        self.assertEqual("REJECT", ap.revision(ficha("REJECTED"))["decision"])
        self.assertEqual("Contradice el acta", ap.revision(ficha("REJECTED", nota="Contradice el acta"))["note"])
        self.assertIsNone(ap.revision(ficha("PENDING")))


class ApiFalsa:
    """Una empresa Ribera con Marta y Javier y los proyectos que se le den. Apunta cada llamada que cambia algo."""

    def __init__(self, proyectos=(), capacidades=("SEARCH_KNOWLEDGE", "CASE_FILES")):
        self.proyectos, self.capacidades, self.cambios = list(proyectos), list(capacidades), []

    def admin(self, metodo, ruta, cuerpo=None, **opciones):
        if metodo != "GET":
            self.cambios.append((metodo, ruta, cuerpo))
            return {"id": "p-nuevo"}
        if ruta == "/v1/admin/companies":
            return [{"id": "c-albar", "code": "ALB", "capabilities": []},
                    {"id": "c-ribera", "code": "RIB", "capabilities": self.capacidades}]
        if ruta.endswith("/members"):
            return [{"userId": "u-marta", "email": "marta@ribera.invalid"}, {"userId": "u-javier", "email": "javier@ribera.invalid"}]
        return [{"id": "p1", "name": n} for n in self.proyectos]


class EsperarTest(unittest.TestCase):

    def test_leeLaListaDelAdministradorConSuTotal(self):
        docs = [{"id": "a", "status": "READY", "title": "A"}, {"id": "b", "status": "READY", "title": "B"}]

        class Api:
            def admin(self, metodo, ruta, cuerpo=None, **opciones):
                return {"documents": docs, "total": len(docs)}

        self.assertEqual([docs[1]], ap.esperar(Api(), "c-ribera", {"b"}))


CREDENCIALES = {"trabajadores": {"marta": {"clave": "k-marta", "proyectos": ["Calle Olmo 14"]},
                                 "javier": {"clave": "k-javier", "proyectos": ["Calle Pino 26"]}}}


class InstalacionTest(unittest.TestCase):

    def test_encuentraLaEmpresaYLosMiembros(self):
        destino = ap.destino(ApiFalsa(), manifiesto(), [EXPEDIENTE], CREDENCIALES, ["marta"])
        self.assertEqual(("c-ribera", {"marta": "u-marta"}), (destino.empresa, destino.miembros))

    def test_siElProyectoYaExisteParaSinCambiarNada(self):
        api = ApiFalsa(proyectos=["Residencial Mirador"])
        with self.assertRaises(SystemExit):
            ap.destino(api, manifiesto(), [], CREDENCIALES, ["marta"])
        self.assertEqual([], api.cambios)

    def test_conExpedientesLaEmpresaTieneQueTenerlos(self):
        with self.assertRaises(SystemExit):
            ap.destino(ApiFalsa(capacidades=["SEARCH_KNOWLEDGE"]), manifiesto(), [EXPEDIENTE], CREDENCIALES, ["marta"])

    def test_unMiembroSinClaveNoPasa(self):
        with self.assertRaises(SystemExit):
            ap.destino(ApiFalsa(), manifiesto(), [], CREDENCIALES, ["lucia"])

    def test_creaElProyectoCerradoYSoloAnadeALosMiembros(self):
        api = ApiFalsa()
        destino = ap.destino(api, manifiesto(), [], CREDENCIALES, ["marta"])
        self.assertEqual("p-nuevo", ap.crear_proyecto(api, destino, PROYECTO))
        self.assertEqual([("POST", "/v1/admin/companies/c-ribera/projects", {"name": "Residencial Mirador"}),
                          ("PUT", "/v1/admin/companies/c-ribera/projects/p-nuevo/members/u-marta", None)], api.cambios)


class AislamientoTest(unittest.TestCase):

    def comprobar(self, listados, buscados=None):
        """listados: ids que lista cada clave en GET /v1/documents (en páginas de 2); buscados: los que encuentra buscando."""
        buscados = buscados or {}

        class Api:
            def admin(self, metodo, ruta, cuerpo=None, clave=None, **opciones):
                if metodo == "POST":
                    return {"hits": [{"sourceId": i} for i in buscados.get(clave, [])]}
                inicio = int(ruta.split("offset=")[1].split("&")[0])
                return [{"id": i} for i in listados[clave][inicio:inicio + 2]]

        return ap.comprobar_aislamiento(Api(), ["consulta"], {"d1", "d2", "d3"}, {"d1", "d2"}, CREDENCIALES, ["marta"], 2)

    def test_elMiembroListaLoLeidoYLosDemasNoVenNada(self):
        self.assertEqual(1, self.comprobar({"k-marta": ["x", "y", "d1", "d2"], "k-javier": ["x"]}))

    def test_siElMiembroNoListaAlgunoYaLeidoFalla(self):
        with self.assertRaises(RuntimeError):
            self.comprobar({"k-marta": ["x", "d1"], "k-javier": []})

    def test_siUnoQueNoEsMiembroLoListaEsFuga(self):
        with self.assertRaises(RuntimeError):
            self.comprobar({"k-marta": ["d1", "d2"], "k-javier": ["x", "y", "d3"]})

    def test_siUnoQueNoEsMiembroLoEncuentraBuscandoEsFuga(self):
        with self.assertRaises(RuntimeError):
            self.comprobar({"k-marta": ["d1", "d2"], "k-javier": []}, {"k-javier": ["d2"]})


class CredencialesTest(unittest.TestCase):

    def test_elProyectoNuevoSoloEnLosMiembros(self):
        cred = ap.con_proyecto(json.loads(json.dumps(CREDENCIALES)), ["marta"], "Residencial Mirador")
        self.assertEqual(["Calle Olmo 14", "Residencial Mirador"], cred["trabajadores"]["marta"]["proyectos"])
        self.assertEqual(["Calle Pino 26"], cred["trabajadores"]["javier"]["proyectos"])


class MainTest(unittest.TestCase):
    """D-099: la orden comprueba los puertos y la confirmación antes de leer los datos o conectar."""

    def lanzar(self, puerto, proyecto, argv_extra=()):
        with tempfile.NamedTemporaryFile("w", suffix=".env", delete=False) as env:
            env.write(f"ODIN_API_PORT={puerto}\nODIN_PANEL_PORT=38088\nODIN_MCP_PORT=38090\n")
        cambios = {"INSTALACION_ENV": env.name, "INSTALACION_PROYECTO": proyecto, "CONFIRMAR_INSTALACION": None}
        with unittest.mock.patch.dict(os.environ, {k: v for k, v in cambios.items() if v}):
            os.environ.pop("CONFIRMAR_INSTALACION", None)
            try:
                with self.assertRaises((SystemExit, FileNotFoundError)) as parada:  # sin parar, llega a leer /no/existe
                    ap.main([*argv_extra, "/no/existe"])
            finally:
                os.unlink(env.name)
        return str(getattr(parada.exception, "code", parada.exception))

    def test_unEnsayoQueApuntaAlPropietarioParaAntesDeLeerNada(self):
        self.assertIn("puertos de la instalación del propietario", self.lanzar(8080, "odin-ensayo"))

    def test_escribirEnLaDelPropietarioSinConfirmarParaAntesDeLeerNada(self):
        self.assertIn("CONFIRMAR_INSTALACION=PROPIETARIO", self.lanzar(8080, "odin"))

    def test_comprobarEnLaDelPropietarioNoPideConfirmacion(self):
        self.assertNotIn("CONFIRMAR_INSTALACION", self.lanzar(8080, "odin", ["--comprobar"]))


if __name__ == "__main__":
    unittest.main()
