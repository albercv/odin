package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.search.SimilarCase;
import com.e2d.odin.domain.search.SimilarCasesQuery;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mide si los casos esperados de cada ficha nueva salen entre los 5 primeros de {@code findSimilarCases}, con los
 * términos de {@code terminos.json} (los escribe una IA que solo ve la ficha, sin las claves).
 */
class SimilarCasesBatteryIT extends AbstractPostgresIT {

    private static final Path DIR = Path.of("src/test/resources/similar-cases-battery");
    private static final Path TERMS = DIR.resolve("terminos.json");
    private static final Path THRESHOLD = DIR.resolve("threshold.txt");
    private static final Map<String, String> PROJECT_NAMES = Map.of("c1-olmo14", "Calle Olmo 14",
            "c2-almendros", "Los Almendros", "c3-puerto3", "Avenida del Puerto 3");
    private static final int LIMIT = 5;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    record Result(int found, int expected, int completeSheets, int sheets, List<String> missing) {
    }

    @Test
    void loaderMarksOnlyCaseSheetsAsCaseDocuments(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("emp"));
        Files.writeString(dir.resolve("emp/caso-1.txt"), "Ficha de caso: filtración.");
        Files.writeString(dir.resolve("emp/acta.txt"), "Acta de junta.");
        CompanyId company = newCompany();
        UserId actor = newUser(company);

        new ArchiveLoader(this).load(company, actor, dir, Map.of("emp", "Proyecto"), SimilarCasesBatteryIT::isCaseSheet);

        var visibility = ProjectVisibility.all();
        List<DocumentEntry> cases = docs.list(CompanyScope.of(company), visibility, Optional.empty(), Optional.empty(),
                Optional.of(true), Optional.empty(), 0, 10);
        assertThat(cases).extracting(e -> e.document().title()).containsExactly("emp/caso-1.txt");
    }

    @Test
    void everySheetHasKeysWithExistingCases() throws IOException {
        for (JsonNode key : JSON.readTree(Files.readString(DIR.resolve("claves.json")))) {
            String id = key.get("id").asString();
            assertThat(DIR.resolve("casos-nuevos/" + id + ".txt")).exists();
            assertThat(key.get("esperados")).isNotEmpty();
            key.get("esperados").forEach(e -> assertThat(DIR.resolve("corpus/" + e.asString())).exists());
        }
    }

    @Test
    void recallAtFiveDoesNotRegress() throws IOException {
        assumeTrue(Files.exists(TERMS), "Falta terminos.json: lo escribe la IA de los términos");

        report(measure(), THRESHOLD);
    }

    @Test
    void thresholdIsWrittenOnFirstRunAndEnforced(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("threshold.txt");
        report(new Result(2, 3, 1, 2, List.of("N2")), file);

        assertThat(Files.readString(file).trim()).isEqualTo("66.66");
        assertThatThrownBy(() -> report(new Result(1, 3, 0, 2, List.of("N1", "N2")), file))
                .isInstanceOf(AssertionError.class);
    }

    static boolean isCaseSheet(String key) {
        return key.substring(key.lastIndexOf('/') + 1).startsWith("caso-");
    }

    private Result measure() throws IOException {
        CompanyId company = newCompany();
        UserId actor = newUser(company);
        LoadedArchive loaded = new ArchiveLoader(this).load(company, actor, DIR.resolve("corpus"), PROJECT_NAMES,
                SimilarCasesBatteryIT::isCaseSheet);
        loaded.projectByFolder().values().forEach(p -> projectMembers.add(company, p, actor));
        ProjectVisibility visibility = ProjectVisibility.of(projectMembers.projectsOf(company, actor));
        JsonNode terms = JSON.readTree(Files.readString(TERMS));
        PostgresKnowledgeSearch search = new PostgresKnowledgeSearch(tx);
        int found = 0;
        int expectedTotal = 0;
        int complete = 0;
        List<String> missing = new ArrayList<>();
        JsonNode keys = JSON.readTree(Files.readString(DIR.resolve("claves.json")));
        for (JsonNode key : keys) {
            String id = key.get("id").asString();
            Set<String> titles = titlesOf(search, company, visibility, terms.get(id).valueStream().map(JsonNode::asString).toList());
            List<String> lost = key.get("esperados").valueStream().map(JsonNode::asString).filter(e -> !titles.contains(e)).toList();
            found += key.get("esperados").size() - lost.size();
            expectedTotal += key.get("esperados").size();
            complete += lost.isEmpty() ? 1 : 0;
            lost.forEach(l -> missing.add(id + "→" + l));
        }
        return new Result(found, expectedTotal, complete, keys.size(), missing);
    }

    private Set<String> titlesOf(PostgresKnowledgeSearch search, CompanyId company, ProjectVisibility visibility,
            List<String> terms) {
        var query = new SimilarCasesQuery(terms, CompanyScope.of(company), visibility, Optional.empty(), LIMIT, Db.NOBODY);
        return search.findSimilarCases(query).cases().stream().map(SimilarCase::title).collect(Collectors.toSet());
    }

    private static void report(Result result, Path thresholdFile) throws IOException {
        double percentage = 100.0 * result.found() / result.expected();
        System.out.printf(Locale.ROOT, "CASOS PARECIDOS %d/%d (%.2f%%) fichas completas=%d/%d fallos=%s%n", result.found(),
                result.expected(), percentage, result.completeSheets(), result.sheets(), result.missing());
        if (!Files.exists(thresholdFile)) {
            Files.writeString(thresholdFile, String.format(Locale.ROOT, "%.2f%n", Math.floor(percentage * 100) / 100));
        }
        double threshold = Double.parseDouble(Files.readString(thresholdFile).trim());
        assertThat(percentage).as("aciertos %d/%d, fallos %s", result.found(), result.expected(), result.missing())
                .isGreaterThanOrEqualTo(threshold);
    }
}
