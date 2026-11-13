"""Pruebas del registro de capas y baterías y de la fusión de varias capas. Con directorios temporales y falsos."""
import contextlib
import io
import json
import re
import tempfile
import unittest
from pathlib import Path

from banco import archivos
from banco.archivos import ArchivoError
from banco.layout import Layout


def write_archive(datos, empresa, **overrides):
    """Capa falsa: una empresa, un trabajador, un documento con su corpus, una ficha y una clave.
    Los nombres salen de la empresa salvo que se indique otro (para provocar repeticiones)."""
    values = {"trabajador": f"{empresa}-t", "ruta": f"{empresa}/_general/doc.txt", "ficha": f"{empresa}-F",
              "clave": f"{empresa}-K", **overrides}
    ruta = values["ruta"]
    (datos / "corpus" / Path(ruta).parent).mkdir(parents=True, exist_ok=True)
    (datos / "corpus" / ruta).write_text(f"=== Página 1 ===\nTexto de {empresa}\n", encoding="utf-8")
    manifest = {"semilla": 7, "empresas": [{"clave": empresa, "nombre": empresa, "codigo": empresa[:3].upper()}],
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
        return archivos.merge([self.ficticio, self.generalidad], out or self.layout.state_dir / "fusion")

    def test_ficticio_alone_keeps_its_own_data_dir(self):
        self.assertEqual(archivos.data_dir(self.layout, ("ficticio",)), self.ficticio)

    def test_several_archives_are_merged_into_state_dir(self):
        out = archivos.data_dir(self.layout, ("ficticio", "generalidad"))
        self.assertEqual(out, self.merged)
        manifest = json.loads((out / "manifiesto.json").read_text(encoding="utf-8"))
        self.assertEqual([e["clave"] for e in manifest["empresas"]], ["ribera", "brumeral"])
        self.assertEqual(manifest["semilla"], 7)
        claves = json.loads((out / "claves.json").read_text(encoding="utf-8"))
        self.assertEqual([c["id"] for c in claves], ["K001", "brumeral-K"])

    def test_corpus_is_copied_not_linked(self):
        out = archivos.data_dir(self.layout, ("ficticio", "generalidad"))
        copied = out / "corpus" / "brumeral" / "_general" / "doc.txt"
        self.assertTrue(copied.is_file())
        self.assertFalse(copied.is_symlink())
        self.assertTrue((out / "corpus" / "ribera" / "_general" / "doc.txt").is_file())

    def test_existing_merged_dir_is_not_rebuilt_by_data_dir(self):
        out = archivos.data_dir(self.layout, ("ficticio", "generalidad"))
        (out / "sentinela.txt").write_text("x", encoding="utf-8")
        archivos.data_dir(self.layout, ("ficticio", "generalidad"))
        self.assertTrue((out / "sentinela.txt").exists())

    def test_merge_rewrites_from_scratch(self):
        out = self.merge_both()
        (out / "sobrante.txt").write_text("x", encoding="utf-8")
        archivos.merge([self.ficticio, self.generalidad], out)
        self.assertFalse((out / "sobrante.txt").exists())

    def test_merge_is_deterministic(self):
        first = self.merge_both(self.layout.state_dir / "uno")
        second = self.merge_both(self.layout.state_dir / "dos")
        for name in ("manifiesto.json", "claves.json", "corpus/brumeral/_general/doc.txt"):
            self.assertEqual((first / name).read_bytes(), (second / name).read_bytes(), name)

    def test_repeated_keys_are_refused(self):
        cases = {
            "empresas": ("ribera", {"trabajador": "x-t", "ruta": "x/doc.txt", "ficha": "x-F", "clave": "x-K"}, "ribera"),
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
                    archivos.merge([self.ficticio, other], self.layout.state_dir / f"fusion-{section}")

    def test_unknown_archive_is_refused(self):
        with self.assertRaisesRegex(ArchivoError, "capa no disponible todavía: publico"):
            archivos.data_dir(self.layout, ("ficticio", "publico"))

    def test_battery_files_and_required_archives(self):
        self.assertEqual(archivos.battery_file(self.layout, "desarrollo"), self.ficticio / "bateria.json")
        self.assertEqual(archivos.battery_file(self.layout, "conector"), self.ficticio / "bateria-conector.json")
        self.assertEqual(archivos.battery_file(self.layout, "generalidad"), self.generalidad / "bateria.json")
        self.assertEqual(archivos.BATTERIES["generalidad"].requires, ("ficticio", "generalidad"))
        self.assertEqual(archivos.BATTERIES["desarrollo"].requires, ("ficticio",))

    def test_items_are_concatenated_in_battery_order(self):
        items = archivos.load_items(self.layout, ("generalidad", "desarrollo"))
        self.assertEqual([i["id"] for i in items], ["brumeral-Q1", "ribera-Q1"])

    def test_missing_battery_file_is_named_in_spanish(self):
        with self.assertRaisesRegex(ArchivoError, "bateria-conector.json"):
            archivos.load_items(self.layout, ("desarrollo", "conector"))

    def test_question_ids_repeated_across_batteries_are_refused(self):
        (self.generalidad / "bateria.json").write_text(json.dumps([{"id": "ribera-Q1"}]), encoding="utf-8")
        with self.assertRaisesRegex(ArchivoError, "ribera-Q1"):
            archivos.load_items(self.layout, ("desarrollo", "generalidad"))

    def test_cli_prints_the_merged_dir(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = archivos.main(["fusionar", "--archivos", "ficticio,generalidad"], layout=self.layout)
        self.assertEqual(code, 0)
        self.assertEqual(out.getvalue().strip(), str(self.merged))

    def test_cli_unknown_archive_is_an_error(self):
        errors = io.StringIO()
        with contextlib.redirect_stderr(errors):
            code = archivos.main(["fusionar", "--archivos", "publico"], layout=self.layout)
        self.assertEqual(code, 1)
        self.assertIn("capa no disponible todavía: publico", errors.getvalue())


if __name__ == "__main__":
    unittest.main()
