package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.search.QuoteMatcher;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SearchBatteryIT extends AbstractPostgresIT {

    private static final Path DIR = Path.of("src/test/resources/search-battery");
    private static final Map<String, String> COMMUNITIES = Map.of("Olmo 14", "c1-olmo14", "Los Almendros",
            "c2-almendros", "Puerto 3", "c3-puerto3");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Map<String, Map<Integer, String>> pagesByDocument;
    private Map<String, ProjectId> projectByFolder;

    @Test
    void batteryRecallAtFiveDoesNotRegress() throws IOException {
        CompanyId company = newCompany();
        loadCorpus(company);
        JsonNode rewrites = JSON.readTree(Files.readString(DIR.resolve("reformulaciones.json")));
        PostgresKnowledgeSearch search = new PostgresKnowledgeSearch(tx);
        List<String> misses = new ArrayList<>();
        int total = 0;
        for (JsonNode item : JSON.readTree(Files.readString(DIR.resolve("bateria.json")))) {
            if (!item.has("citas")) {
                continue;
            }
            total++;
            if (!answered(search, company, item, rewrites)) {
                misses.add(item.get("id").asString());
            }
        }
        report(total - misses.size(), total, misses);
    }

    private boolean answered(PostgresKnowledgeSearch search, CompanyId company, JsonNode item, JsonNode rewrites) {
        String id = item.get("id").asString();
        String question = item.get("pregunta").asString();
        List<String> terms = rewrites.has(id) ? rewrites.get(id).valueStream().map(JsonNode::asString).toList()
                : List.of(question);
        Optional<ProjectId> project = COMMUNITIES.entrySet().stream().filter(e -> question.contains(e.getKey()))
                .findFirst().map(e -> projectByFolder.get(e.getValue()));
        List<SearchHit> hits = search.search(new SearchQuery(terms, CompanyScope.of(company), ProjectVisibility.all(), project, 5, Db.NOBODY)).hits();
        List<String> expected = expectedPages(item);
        return hits.stream().anyMatch(h -> expected.contains(h.title() + "#" + h.pageNumber().orElse(0)));
    }

    private List<String> expectedPages(JsonNode item) {
        List<String> expected = new ArrayList<>();
        for (JsonNode cite : item.get("citas")) {
            String document = cite.get(0).asString();
            pagesByDocument.get(document).forEach((number, text) -> {
                if (QuoteMatcher.containsLiteral(text, cite.get(1).asString())) {
                    expected.add(document + "#" + number);
                }
            });
        }
        return expected;
    }

    private void loadCorpus(CompanyId company) throws IOException {
        Map<String, String> namesByFolder = new LinkedHashMap<>();
        COMMUNITIES.forEach((name, folder) -> namesByFolder.put(folder, name));
        LoadedArchive loaded = new ArchiveLoader(this).load(company, newUser(company), DIR.resolve("corpus-limpio"),
                namesByFolder);
        pagesByDocument = loaded.pagesByDocument();
        projectByFolder = loaded.projectByFolder();
    }

    private void report(int hits, int total, List<String> misses) throws IOException {
        double percentage = 100.0 * hits / total;
        System.out.printf(Locale.ROOT, "BATERIA %d/%d (%.2f%%) fallos=%s%n", hits, total, percentage, misses);
        Path file = DIR.resolve("threshold.txt");
        if (!Files.exists(file)) {
            Files.writeString(file, String.format(Locale.ROOT, "%.2f%n", Math.floor(percentage * 100) / 100));
        }
        double threshold = Double.parseDouble(Files.readString(file).trim());
        assertThat(percentage).as("aciertos %d/%d, fallos %s", hits, total, misses).isGreaterThanOrEqualTo(threshold);
    }
}
