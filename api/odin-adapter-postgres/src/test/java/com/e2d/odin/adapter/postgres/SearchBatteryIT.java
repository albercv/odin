package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.search.QuoteMatcher;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SearchBatteryIT extends AbstractPostgresIT {

    private static final Path DIR = Path.of("src/test/resources/search-battery");
    private static final Pattern PAGE_MARK = Pattern.compile("^=== Página (\\d+) ===\\s*$", Pattern.MULTILINE);
    private static final Map<String, String> COMMUNITIES = Map.of("Olmo 14", "c1-olmo14", "Los Almendros",
            "c2-almendros", "Puerto 3", "c3-puerto3");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, Map<Integer, String>> pagesByDocument = new LinkedHashMap<>();
    private final Map<String, ProjectId> projectByFolder = new LinkedHashMap<>();

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
        List<SearchHit> hits = search.search(new SearchQuery(terms, CompanyScope.of(company), project, 5)).hits();
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

    private void loadCorpus(CompanyId company) throws IOException {
        UserId uploader = newUser(company);
        for (Map.Entry<String, String> community : COMMUNITIES.entrySet()) {
            ProjectId project = new ProjectId(UUID.randomUUID());
            orgs.saveProject(new Project(project, company, community.getKey()));
            projectByFolder.put(community.getValue(), project);
            try (var files = Files.list(DIR.resolve("corpus-limpio").resolve(community.getValue()))) {
                for (Path file : files.sorted().toList()) {
                    loadDocument(company, uploader, project, community.getValue() + "/" + file.getFileName(), file);
                }
            }
        }
    }

    private void loadDocument(CompanyId company, UserId uploader, ProjectId project, String key, Path file)
            throws IOException {
        Document d = new Document(new DocumentId(UUID.randomUUID()), company, Optional.of(project), key,
                file.getFileName().toString(), DocumentFormat.PLAIN_TEXT, UUID.randomUUID().toString(),
                DocumentStatus.READY, Optional.empty(), true, uploader, NOW, Optional.empty(), 0);
        docs.save(d);
        Map<Integer, String> pages = splitPages(Files.readString(file));
        pagesByDocument.put(key, pages);
        docs.replacePages(d.id(), pages.entrySet().stream()
                .map(p -> new DocumentPage(d.id(), p.getKey(), p.getValue(), "corpus", "1")).toList());
    }

    private static Map<Integer, String> splitPages(String content) {
        Map<Integer, String> pages = new LinkedHashMap<>();
        Matcher marks = PAGE_MARK.matcher(content);
        int number = 0;
        int start = 0;
        while (marks.find()) {
            if (number > 0) {
                pages.put(number, content.substring(start, marks.start()).strip());
            }
            number = Integer.parseInt(marks.group(1));
            start = marks.end();
        }
        pages.put(number, content.substring(start).strip());
        return pages;
    }
}
