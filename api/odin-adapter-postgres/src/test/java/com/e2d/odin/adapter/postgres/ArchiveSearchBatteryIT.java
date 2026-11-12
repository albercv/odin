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
        ArchiveBattery.report(new ArchiveBattery.Result(2, 3, 0, List.of("Q3")), file);

        assertThat(Files.readString(file).trim()).isEqualTo("66.66");
        assertThatThrownBy(() -> ArchiveBattery.report(new ArchiveBattery.Result(1, 3, 0, List.of("Q2", "Q3")), file))
                .isInstanceOf(AssertionError.class);
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
        return ArchiveBattery.measure(this, items, byActor, loaded.pagesByDocument());
    }
}
