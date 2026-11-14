"""Pruebas del registro de capas y baterías y de la fusión de varias capas. Con directorios temporales y falsos."""
import contextlib
import io
import json
import os
import re
import shutil
import tempfile
import unittest
from unittest import mock
from pathlib import Path

from banco import capas
from banco.capas import ArchivoError
from banco.layout import Layout


def write_archive(datos, empresa, **overrides):
    """Capa falsa: una empresa, un trabajador, un documento con su corpus, una ficha y una clave.
    Los nombres salen de la empresa salvo que se indique otro (para provocar repeticiones)."""
    values = {"trabajador": f"{empresa}-t", "ruta": f"{empresa}/_general/doc.txt", "ficha": f"{empresa}-F",
              "clave": f"{empresa}-K", **overrides}
    ruta = values["ruta"]
    (datos / "corpus" / Path(ruta).parent).mkdir(parents=True, exist_ok=True)
    (datos / "corpus" / ruta).write_text(f"=== Página 1 ===\nTexto de {empresa}\n", encoding="utf-8")
    manifest = {"semilla": 7, "empresas": [{"clave": empresa, "nombre": values.get("nombre", empresa), "codigo": empresa[:3].upper()}],
                "proyectos": [],
                "trabajadores": [{"clave": values["trabajador"], "empresa": empresa, "nombre": "Ana",
                                  "apellidos": "Pérez", "proyectos": ["*"]}],
                "documentos": [{"ruta": ruta, "empresa": empresa, "proyecto": None, "titulo": "Doc",
                                "subido": "2025-01-01T00:00:00Z", "subido_por": values["trabajador"]}],
                "fichas": [{"id": values["ficha"], "empresa": empresa, "proyecto": None, "autor": values["trabajador"],
                            "estado": "APPROVED", "titulo": "Ficha", "texto": "Texto.", "creada": "2025-01-01T00:00:00Z"}]}
    (datos / "manifiesto.json").write_text(json.dumps(manifest, ensure_ascii=False), encoding="utf-8")
    (datos / "claves.json").write_text(json.dumps([{"id": values["clave"]}], ensure_ascii=False), encoding="utf-8")
    (datos / "bateria.json").write_text(json.dumps([{"id": f"{empresa}-Q1"}]), encoding="utf-8")


def write_expedientes(datos, caso, documentos=(), compartidos=(), ajenos=(), write_files=True):
    """Expediente falso: `caso` es una ruta real del corpus (así la busca el cargador). Sus ficheros van en expedientes/."""
    case = {"caso": caso, "proyecto": None, "documentos": list(documentos), "compartidos": list(compartidos),
            "ajenos": list(ajenos), "formas_numero": [], "sustituciones": []}
    (datos / "expedientes.json").write_text(json.dumps([case], ensure_ascii=False), encoding="utf-8")
    for ruta in [*documentos, *compartidos, *ajenos] if write_files else []:
        fichero = datos / "expedientes" / ruta
        fichero.parent.mkdir(parents=True, exist_ok=True)
        fichero.write_text("=== Página 1 ===\nTexto del expediente\n", encoding="utf-8")


def make_layout(root):
    return Layout(banco=root / "banco", exp=root / "exp", state_dir=root / "estado", results_dir=root / "resultados",
                  cache=root / "cache", repo=root, claude_bin="x", claude_judge_bin="x", ollama_url="", timeout=1)


class ArchivesTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.layout = make_layout(Path(tmp.name))
        self.ficticio = self.layout.exp / "datos"
        self.generalidad = self.layout.banco / "archivos" / "generalidad" / "datos"
        write_archive(self.ficticio, "ribera", ficha="F001", clave="K001", trabajador="ribera-t", ruta="ribera/_general/doc.txt")
        write_archive(self.generalidad, "brumeral")
        self.merged = self.layout.state_dir / "archivo-ficticio+generalidad"

    def merge_both(self, out=None):
        return capas.merge([self.ficticio, self.generalidad], out or self.layout.state_dir / "fusion")

    def test_ficticio_alone_keeps_its_own_data_dir(self):
        self.assertEqual(capas.data_dir(self.layout, ("ficticio",)), self.ficticio)

    def test_several_archives_are_merged_into_state_dir(self):
        out = capas.data_dir(self.layout, ("ficticio", "generalidad"))
        self.assertEqual(out, self.merged)
        manifest = json.loads((out / "manifiesto.json").read_text(encoding="utf-8"))
        self.assertEqual([e["clave"] for e in manifest["empresas"]], ["ribera", "brumeral"])
        self.assertEqual(manifest["semilla"], 7)
        claves = json.loads((out / "claves.json").read_text(encoding="utf-8"))
        self.assertEqual([c["id"] for c in claves], ["K001", "brumeral-K"])

    def test_corpus_is_copied_not_linked(self):
        out = capas.data_dir(self.layout, ("ficticio", "generalidad"))
        copied = out / "corpus" / "brumeral" / "_general" / "doc.txt"
        self.assertTrue(copied.is_file())
        self.assertFalse(copied.is_symlink())
        self.assertTrue((out / "corpus" / "ribera" / "_general" / "doc.txt").is_file())

    def test_existing_merged_dir_is_not_rebuilt_by_data_dir(self):
        out = capas.data_dir(self.layout, ("ficticio", "generalidad"))
        (out / "sentinela.txt").write_text("x", encoding="utf-8")
        capas.data_dir(self.layout, ("ficticio", "generalidad"))
        self.assertTrue((out / "sentinela.txt").exists())

    def test_merge_rewrites_from_scratch(self):
        out = self.merge_both()
        (out / "sobrante.txt").write_text("x", encoding="utf-8")
        capas.merge([self.ficticio, self.generalidad], out)
        self.assertFalse((out / "sobrante.txt").exists())

    def test_merge_is_deterministic(self):
        first = self.merge_both(self.layout.state_dir / "uno")
        second = self.merge_both(self.layout.state_dir / "dos")
        for name in ("manifiesto.json", "claves.json", "corpus/brumeral/_general/doc.txt"):
            self.assertEqual((first / name).read_bytes(), (second / name).read_bytes(), name)

    def test_repeated_keys_are_refused(self):
        cases = {
            "trabajadores": ("brumeral", {"trabajador": "ribera-t"}, "ribera-t"),
            "documentos": ("brumeral", {"ruta": "ribera/_general/doc.txt"}, "ribera/_general/doc.txt"),
            "fichas": ("brumeral", {"ficha": "F001"}, "F001"),
            "claves": ("brumeral", {"clave": "K001"}, "K001"),
        }
        for section, (empresa, overrides, value) in cases.items():
            with self.subTest(section=section):
                other = self.layout.state_dir / f"otra-{section}"
                write_archive(other, empresa, **overrides)
                with self.assertRaisesRegex(ArchivoError, re.escape(f"repetida en {section}: {value}")):
                    capas.merge([self.ficticio, other], self.layout.state_dir / f"fusion-{section}")

    def test_unknown_archive_is_refused(self):
        with self.assertRaisesRegex(ArchivoError, "capa no disponible todavía: publico"):
            capas.data_dir(self.layout, ("ficticio", "publico"))

    def test_missing_corpus_dir_is_refused(self):
        shutil.rmtree(self.generalidad / "corpus")
        with self.assertRaisesRegex(ArchivoError, "falta el corpus"):
            self.merge_both()

    def test_document_missing_from_corpus_is_refused(self):
        (self.generalidad / "corpus" / "brumeral" / "_general" / "doc.txt").unlink()
        with self.assertRaisesRegex(ArchivoError, re.escape("falta en el corpus: brumeral/_general/doc.txt")):
            self.merge_both()

    def test_interrupted_merge_leaves_no_partial_dir(self):
        with mock.patch("banco.capas.shutil.copyfile", side_effect=OSError("corte")):
            with self.assertRaises(OSError):
                capas.data_dir(self.layout, ("ficticio", "generalidad"))
        self.assertFalse(self.merged.exists())
        self.assertEqual(capas.data_dir(self.layout, ("ficticio", "generalidad")), self.merged)
        self.assertTrue((self.merged / "manifiesto.json").is_file())

    def test_data_dir_rebuilds_when_manifest_is_missing(self):
        self.merged.mkdir(parents=True)
        (self.merged / "restos.txt").write_text("x", encoding="utf-8")
        capas.data_dir(self.layout, ("ficticio", "generalidad"))
        self.assertTrue((self.merged / "manifiesto.json").is_file())
        self.assertFalse((self.merged / "restos.txt").exists())

    def test_battery_files_and_required_archives(self):
        self.assertEqual(capas.battery_file(self.layout, "desarrollo"), self.ficticio / "bateria.json")
        self.assertEqual(capas.battery_file(self.layout, "conector"), self.ficticio / "bateria-conector.json")
        self.assertEqual(capas.battery_file(self.layout, "generalidad"), self.generalidad / "bateria.json")
        self.assertEqual(capas.BATTERIES["generalidad"].requires, ("ficticio", "generalidad"))
        self.assertEqual(capas.BATTERIES["desarrollo"].requires, ("ficticio",))

    def test_items_are_concatenated_in_battery_order(self):
        items = capas.load_items(self.layout, ("generalidad", "desarrollo"))
        self.assertEqual([i["id"] for i in items], ["brumeral-Q1", "ribera-Q1"])

    def test_missing_battery_file_is_named_in_spanish(self):
        with self.assertRaisesRegex(ArchivoError, "bateria-conector.json"):
            capas.load_items(self.layout, ("desarrollo", "conector"))

    def test_question_ids_repeated_across_batteries_are_refused(self):
        (self.generalidad / "bateria.json").write_text(json.dumps([{"id": "ribera-Q1"}]), encoding="utf-8")
        with self.assertRaisesRegex(ArchivoError, "ribera-Q1"):
            capas.load_items(self.layout, ("desarrollo", "generalidad"))

    def test_cli_prints_the_merged_dir(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = capas.main(["fusionar", "--archivos", "ficticio,generalidad"], layout=self.layout)
        self.assertEqual(code, 0)
        self.assertEqual(out.getvalue().strip(), str(self.merged))

    def test_cli_unknown_archive_is_an_error(self):
        errors = io.StringIO()
        with contextlib.redirect_stderr(errors):
            code = capas.main(["fusionar", "--archivos", "publico"], layout=self.layout)
        self.assertEqual(code, 1)
        self.assertIn("capa no disponible todavía: publico", errors.getvalue())


    def test_same_company_in_two_layers_is_accepted_once(self):
        other = self.layout.state_dir / "otra-identica"
        write_archive(other, "ribera", ficha="F002", clave="K002", trabajador="ribera-t2", ruta="ribera/_general/otro.txt")
        capas.merge([self.ficticio, other], self.layout.state_dir / "fusion-identica")
        manifest = json.loads((self.layout.state_dir / "fusion-identica" / "manifiesto.json").read_text(encoding="utf-8"))
        self.assertEqual([e["clave"] for e in manifest["empresas"]], ["ribera"])

    def test_conflicting_company_in_two_layers_is_refused(self):
        other = self.layout.state_dir / "otra-distinta"
        write_archive(other, "ribera", nombre="Ribera Norte", ruta="ribera/_general/otro.txt", ficha="F002", clave="K002")
        with self.assertRaisesRegex(ArchivoError, re.escape("empresa repetida con datos distintos: ribera")):
            capas.merge([self.ficticio, other], self.layout.state_dir / "fusion-distinta")


class MiradorArchiveTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.layout = make_layout(self.root)
        self.ficticio = self.layout.exp / "datos"
        write_archive(self.ficticio, "ribera", ficha="F001", clave="K001", trabajador="ribera-t", ruta="ribera/_general/doc.txt")
        self.mirador = self.root / "mirador"

    def write_mirador(self, textos, ruta="ribera/_general/mirador.txt"):
        """Capa de Mirador: manifiesto con un documento, claves y textos por página; sin corpus/ (lo dan los textos)."""
        self.mirador.mkdir(parents=True, exist_ok=True)
        manifest = {"semilla": 7, "empresas": [{"clave": "ribera", "nombre": "ribera", "codigo": "RIB"}], "proyectos": [],
                    "trabajadores": [], "fichas": [],
                    "documentos": [{"ruta": ruta, "empresa": "ribera", "proyecto": None, "titulo": "Mirador",
                                    "subido": "2025-01-01T00:00:00Z", "subido_por": "ribera-t"}]}
        (self.mirador / "manifiesto.json").write_text(json.dumps(manifest), encoding="utf-8")
        (self.mirador / "claves.json").write_text("[]", encoding="utf-8")
        (self.mirador / "textos.json").write_text(json.dumps({ruta: textos}, ensure_ascii=False), encoding="utf-8")
        (self.mirador / "bateria-mirador.json").write_text(json.dumps([{"id": "M01"}]), encoding="utf-8")

    def test_unset_env_is_a_spanish_error(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(ArchivoError, "ODIN_BANCO_MIRADOR"):
                capas.archive_dir(self.layout, "mirador")

    def test_missing_folder_is_a_spanish_error(self):
        with mock.patch.dict(os.environ, {"ODIN_BANCO_MIRADOR": str(self.root / "no-existe")}):
            with self.assertRaisesRegex(ArchivoError, "no existe"):
                capas.archive_dir(self.layout, "mirador")

    def test_battery_lives_in_mirador_folder_and_needs_both_archives(self):
        self.write_mirador({"1": "Texto"})
        with mock.patch.dict(os.environ, {"ODIN_BANCO_MIRADOR": str(self.mirador)}):
            self.assertEqual(capas.battery_file(self.layout, "mirador"), self.mirador / "bateria-mirador.json")
        self.assertEqual(capas.BATTERIES["mirador"].requires, ("ficticio", "mirador"))

    def test_merge_takes_textos_and_skips_missing_corpus(self):
        self.write_mirador({"1": "Página uno", "2": "Página dos"})
        out = self.root / "fusion"
        capas.merge([self.ficticio, self.mirador], out)
        textos = json.loads((out / "textos.json").read_text(encoding="utf-8"))
        self.assertEqual(textos, {"ribera/_general/mirador.txt": {"1": "Página uno", "2": "Página dos"}})
        self.assertTrue((out / "corpus" / "ribera" / "_general" / "doc.txt").is_file())
        self.assertFalse((out / "corpus" / "ribera" / "_general" / "mirador.txt").exists())

    def test_document_without_file_or_textos_is_refused(self):
        self.write_mirador({"1": "Texto"}, ruta="ribera/_general/otro.txt")
        (self.mirador / "textos.json").write_text(json.dumps({"ribera/_general/distinto.txt": {"1": "x"}}), encoding="utf-8")
        with self.assertRaisesRegex(ArchivoError, re.escape("falta en el corpus: ribera/_general/otro.txt")):
            capas.merge([self.ficticio, self.mirador], self.root / "fusion")

    def test_repeated_textos_ruta_is_refused(self):
        self.write_mirador({"1": "Texto"})
        other = self.root / "otra"
        other.mkdir()
        (other / "manifiesto.json").write_text(json.dumps({"semilla": 7, "empresas": [], "proyectos": [], "trabajadores": [],
                                                           "documentos": [], "fichas": []}), encoding="utf-8")
        (other / "claves.json").write_text("[]", encoding="utf-8")
        (other / "textos.json").write_text(json.dumps({"ribera/_general/mirador.txt": {"1": "Otro"}}), encoding="utf-8")
        with self.assertRaisesRegex(ArchivoError, re.escape("ruta repetida en los textos: ribera/_general/mirador.txt")):
            capas.merge([self.mirador, other], self.root / "fusion")

    def test_ficticio_only_writes_no_textos(self):
        capas.merge([self.ficticio], self.root / "solo")
        self.assertFalse((self.root / "solo" / "textos.json").exists())


class ExpedientesMergeTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.ficticio = self.root / "ficticio"
        self.generalidad = self.root / "generalidad"
        write_archive(self.ficticio, "ribera")
        write_archive(self.generalidad, "brumeral")
        self.out = self.root / "fusion"

    def test_expedientes_are_concatenated_and_copied(self):
        write_expedientes(self.ficticio, "ribera/_general/doc.txt", ["ribera/proyecto/c1.txt"])
        write_expedientes(self.generalidad, "brumeral/_general/doc.txt", ["brumeral/proyecto/c1.txt"])
        capas.merge([self.ficticio, self.generalidad], self.out)
        cases = json.loads((self.out / "expedientes.json").read_text(encoding="utf-8"))
        self.assertEqual([c["caso"] for c in cases], ["ribera/_general/doc.txt", "brumeral/_general/doc.txt"])
        copied = self.out / "expedientes" / "brumeral" / "proyecto" / "c1.txt"
        self.assertTrue(copied.is_file())
        self.assertFalse(copied.is_symlink())

    def test_layers_without_expedientes_write_nothing(self):
        capas.merge([self.ficticio, self.generalidad], self.out)
        self.assertFalse((self.out / "expedientes.json").exists())
        self.assertFalse((self.out / "expedientes").exists())

    def test_repeated_caso_is_refused(self):
        write_expedientes(self.ficticio, "ribera/_general/doc.txt", ["ribera/proyecto/c1.txt"])
        write_expedientes(self.generalidad, "ribera/_general/doc.txt", ["brumeral/proyecto/c1.txt"])
        with self.assertRaisesRegex(ArchivoError, "clave repetida en expedientes: ribera/_general/doc.txt"):
            capas.merge([self.ficticio, self.generalidad], self.out)

    def test_repeated_expediente_file_is_refused(self):
        write_expedientes(self.ficticio, "ribera/_general/doc.txt", ["ribera/proyecto/c1.txt"])
        write_expedientes(self.generalidad, "brumeral/_general/doc.txt", ["ribera/proyecto/c1.txt"])
        with self.assertRaisesRegex(ArchivoError, re.escape("fichero repetido en los expedientes: ribera/proyecto/c1.txt")):
            capas.merge([self.ficticio, self.generalidad], self.out)

    def test_expediente_ruta_also_in_corpus_is_refused(self):
        for key in ("documentos", "ajenos"):
            with self.subTest(key=key):
                write_expedientes(self.ficticio, "ribera/_general/doc.txt", **{key: ["ribera/_general/doc.txt"]})
                with self.assertRaisesRegex(ArchivoError, re.escape(
                        "ruta repetida en el corpus y en los expedientes: ribera/_general/doc.txt")):
                    capas.merge([self.ficticio, self.generalidad], self.out)

    def test_listed_expediente_file_missing_is_refused(self):
        for key in ("documentos", "compartidos", "ajenos"):
            with self.subTest(key=key):
                write_expedientes(self.ficticio, "ribera/_general/doc.txt", write_files=False,
                                  **{key: ["ribera/proyecto/falta.txt"]})
                with self.assertRaisesRegex(ArchivoError, re.escape("falta en los expedientes: ribera/proyecto/falta.txt")):
                    capas.merge([self.ficticio, self.generalidad], self.out)


class ExpedienteBatteryTest(unittest.TestCase):
    def test_expediente_battery_is_in_generalidad_and_needs_both_archives(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        layout = make_layout(Path(tmp.name))
        self.assertEqual(capas.battery_file(layout, "generalidad-expediente"),
                         layout.banco / "archivos" / "generalidad" / "datos" / "bateria-expediente.json")
        self.assertEqual(capas.BATTERIES["generalidad-expediente"].requires, ("ficticio", "generalidad"))


if __name__ == "__main__":
    unittest.main()
