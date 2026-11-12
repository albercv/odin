package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ArchiveSearchBatteryIT extends AbstractPostgresIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void archiveSearchRecallAtTenDoesNotRegress() throws IOException {
        ArchiveBattery.Result result = ArchiveBattery.measureArchive(this);

        ArchiveBattery.report(result, ArchiveBattery.THRESHOLD);
    }

    @Test
    void questionWithoutProjectNameIsSkippedAndCounted(@TempDir Path dir) throws IOException {
        ArchiveBattery.Result result = measureTinyArchive(dir, """
                [{"id":"Q1","actor":"t01","pregunta":"¿Qué franquicia tiene Calle Uno?",
                  "citas_resueltas":[{"doc":"emp/p1/a.txt","paginas":[1],"ancla":"franquicia de 150 euros"}]},
                 {"id":"Q2","actor":"t01","pregunta":"¿Qué franquicia hay?",
                  "citas_resueltas":[{"doc":"emp/p1/a.txt","paginas":[1],"ancla":"franquicia de 150 euros"}]}]""");

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.hits()).isEqualTo(1);
        assertThat(result.withoutProject()).isEqualTo(1);
    }

    @Test
    void thresholdIsWrittenOnFirstRunAndEnforced(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("threshold.txt");
        ArchiveBattery.report(new ArchiveBattery.Result(2, 3, 0, List.of("Q3"), List.of()), file);

        assertThat(Files.readString(file).trim()).isEqualTo("66.66");
        assertThatThrownBy(() -> ArchiveBattery.report(new ArchiveBattery.Result(1, 3, 0, List.of("Q2", "Q3"), List.of()), file))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void reportFailsAtTheEndListingTheSupersessionProblems(@TempDir Path dir) throws IOException {
        ArchiveBattery.Result result = new ArchiveBattery.Result(3, 3, 0, List.of(), List.of("Q7 «a.txt»: es viejo y sale sin marca"));

        assertThatThrownBy(() -> ArchiveBattery.report(result, dir.resolve("threshold.txt")))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Q7 «a.txt»: es viejo y sale sin marca");
    }

    @Test
    void marksFromTheKeysAreAppliedAndSearchShowsThemAfterTheCurrentOne(@TempDir Path dir) throws IOException {
        Path keys = twoVersionsArchive(dir);
        CompanyId company = newCompany();
        UserId actor = newUser(company);
        Map<String, String> names = Map.of("emp/p1", "Calle Uno");
        ArchiveLoader loader = new ArchiveLoader(this);
        LoadedArchive loaded = loader.load(company, actor, dir, names);

        Map<String, String> marks = loader.applySupersessions(company, keys);

        assertThat(marks).containsExactly(Map.entry("emp/p1/antigua.txt", "emp/p1/vigente.txt"));
        ArchiveBattery.Company c = ArchiveBattery.companyOf(this, company, actor, loaded, names);
        ArchiveBattery.Result result = ArchiveBattery.measure(this, JSON.readTree("""
                [{"id":"Q1","actor":"t01","pregunta":"¿Qué franquicia tiene Calle Uno?",
                  "citas_resueltas":[{"doc":"emp/p1/vigente.txt","paginas":[1],"ancla":"franquicia de 300 euros"}]}]"""),
                Map.of("t01", c), loaded.pagesByDocument(), marks);
        assertThat(result.problems()).isEmpty();
        assertThat(result.hits()).isEqualTo(1);
    }

    @Test
    void measureReportsAnOldDocumentThatIsNotMarked(@TempDir Path dir) throws IOException {
        twoVersionsArchive(dir);
        CompanyId company = newCompany();
        UserId actor = newUser(company);
        Map<String, String> names = Map.of("emp/p1", "Calle Uno");
        LoadedArchive loaded = new ArchiveLoader(this).load(company, actor, dir, names);

        ArchiveBattery.Result result = ArchiveBattery.measure(this, JSON.readTree("""
                [{"id":"Q1","actor":"t01","pregunta":"¿Qué franquicia tiene Calle Uno?",
                  "citas_resueltas":[{"doc":"emp/p1/vigente.txt","paginas":[1],"ancla":"franquicia de 300 euros"}]}]"""),
                Map.of("t01", ArchiveBattery.companyOf(this, company, actor, loaded, names)), loaded.pagesByDocument(),
                Map.of("emp/p1/antigua.txt", "emp/p1/vigente.txt"));

        assertThat(result.problems()).anyMatch(p -> p.contains("Q1") && p.contains("emp/p1/antigua.txt"));
    }

    @Test
    void coupleOutsideTheCorpusStops(@TempDir Path dir) throws IOException {
        Path keys = twoVersionsArchive(dir);
        Files.delete(dir.resolve("emp/p1/vigente.txt"));
        CompanyId company = newCompany();
        ArchiveLoader loader = new ArchiveLoader(this);
        loader.load(company, newUser(company), dir, Map.of("emp/p1", "Calle Uno"));

        assertThatThrownBy(() -> loader.applySupersessions(company, keys)).isInstanceOf(IllegalStateException.class)
                .hasMessage("La pareja emp/p1/antigua.txt → emp/p1/vigente.txt no está en el corpus");
    }

    private Path twoVersionsArchive(Path dir) throws IOException {
        Files.createDirectories(dir.resolve("emp/p1"));
        Files.writeString(dir.resolve("emp/p1/antigua.txt"), "Póliza antigua: franquicia de 490 euros por siniestro de agua.");
        Files.writeString(dir.resolve("emp/p1/vigente.txt"), "Póliza vigente: franquicia de 300 euros por siniestro de agua.");
        Path keys = dir.resolve("claves.json");
        Files.writeString(keys, """
                [{"id":"K1","tipo":"version","vigente":{"doc":"emp/p1/vigente.txt"},
                  "fuentes":[{"doc":"emp/p1/antigua.txt","pagina":1,"frase":"490"},{"doc":"emp/p1/vigente.txt","pagina":1,"frase":"300"}]},
                 {"id":"K2","tipo":"anexo","vigente":null,"fuentes":[{"doc":"emp/p1/vigente.txt","pagina":1,"frase":"x"}]}]""");
        return keys;
    }

    private ArchiveBattery.Result measureTinyArchive(Path dir, String questions) throws IOException {
        Files.createDirectories(dir.resolve("emp/p1"));
        Files.writeString(dir.resolve("emp/p1/a.txt"), "Cláusula: franquicia de 150 euros por siniestro.");
        CompanyId company = newCompany();
        UserId actor = newUser(company);
        Map<String, String> names = Map.of("emp/p1", "Calle Uno");
        LoadedArchive loaded = new ArchiveLoader(this).load(company, actor, dir, names);
        Map<String, ArchiveBattery.Company> byActor = new LinkedHashMap<>();
        byActor.put("t01", ArchiveBattery.companyOf(this, company, actor, loaded, names));
        JsonNode items = JSON.readTree(questions);
        return ArchiveBattery.measure(this, items, byActor, loaded.pagesByDocument(), Map.of());
    }
}
