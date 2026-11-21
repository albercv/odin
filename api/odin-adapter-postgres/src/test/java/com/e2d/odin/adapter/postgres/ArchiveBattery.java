package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.search.QuoteMatcher;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Mide si la página esperada de cada pregunta del archivo ficticio sale entre los 10 primeros con el filtro de su proyecto. */
final class ArchiveBattery {

    static final Path DIR = Path.of(System.getProperty("odin.archive.dir",
            "../../datos-prueba/archivo-ficticio/datos"));
    static final Path THRESHOLD = Path.of("src/test/resources/archive-battery/threshold.txt");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int LIMIT = 10;

    /** Una empresa cargada: lo que ve su actor y sus proyectos por nombre. */
    record Company(CompanyId id, ProjectVisibility visibility, Map<String, ProjectId> projectByName) {
    }

    record Result(int hits, int total, int withoutProject, List<String> misses, List<String> problems) {
    }

    private ArchiveBattery() {
    }

    static Result measureArchive(AbstractPostgresIT it) throws IOException {
        if (!Files.isDirectory(DIR)) {
            throw new AssertionError("Falta el archivo ficticio en " + DIR.toAbsolutePath()
                    + " (cambia la ruta con -Dodin.archive.dir)");
        }
        JsonNode manifest = JSON.readTree(Files.readString(DIR.resolve("manifiesto.json")));
        Map<String, Company> companyByKey = new LinkedHashMap<>();
        Map<String, Map<Integer, String>> pages = new LinkedHashMap<>();
        ArchiveLoader loader = new ArchiveLoader(it);
        Map<String, CompanyId> idByKey = new LinkedHashMap<>();
        for (JsonNode company : manifest.get("empresas")) {
            String key = company.get("clave").asString();
            Map<String, String> names = folderNames(manifest, key);
            CompanyId id = it.newCompany();
            UserId actor = it.newUser(id);
            LoadedArchive loaded = loader.load(id, actor, DIR.resolve("corpus"), names);
            idByKey.put(key, id);
            companyByKey.put(key, companyOf(it, id, actor, loaded, names));
            pages.putAll(loaded.pagesByDocument());
        }
        Map<String, String> supersededBy = applySupersessions(loader, idByKey.values());
        Map<String, Company> byActor = new LinkedHashMap<>();
        manifest.get("trabajadores").forEach(w -> byActor.put(w.get("clave").asString(),
                companyByKey.get(w.get("empresa").asString())));
        return measure(it, JSON.readTree(Files.readString(DIR.resolve("bateria.json"))), byActor, pages,
                supersededBy);
    }

    private static Map<String, String> applySupersessions(ArchiveLoader loader, Iterable<CompanyId> companies)
            throws IOException {
        Map<String, String> supersededBy = new LinkedHashMap<>();
        for (CompanyId id : companies) {
            supersededBy.putAll(loader.applySupersessions(id, DIR.resolve("claves.json")));
        }
        System.out.printf(Locale.ROOT, "ARCHIVO marcas de sustitución: %d%n", supersededBy.size());
        return supersededBy;
    }

    /** Carpeta {@code empresa/proyecto} -> nombre, más la carpeta general (sin proyecto). */
    static Map<String, String> folderNames(JsonNode manifest, String company) {
        Map<String, String> names = new LinkedHashMap<>();
        names.put(company + "/_general", null);
        for (JsonNode project : manifest.get("proyectos")) {
            if (project.get("empresa").asString().equals(company)) {
                names.put(company + "/" + project.get("clave").asString(), project.get("nombre").asString());
            }
        }
        return names;
    }

    static Company companyOf(AbstractPostgresIT it, CompanyId company, UserId actor, LoadedArchive loaded,
            Map<String, String> namesByFolder) {
        Map<String, ProjectId> projectByName = new LinkedHashMap<>();
        loaded.projectByFolder().forEach((folder, project) -> {
            it.projectMembers.add(company, project, actor);
            projectByName.put(namesByFolder.get(folder), project);
        });
        return new Company(company, ProjectVisibility.of(it.projectMembers.projectsOf(company, actor)), projectByName);
    }

    static Result measure(AbstractPostgresIT it, JsonNode items, Map<String, Company> byActor,
            Map<String, Map<Integer, String>> pagesByDocument, Map<String, String> supersededBy) {
        PostgresKnowledgeSearch search = new PostgresKnowledgeSearch(it.tx);
        List<String> misses = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        int total = 0;
        int withoutProject = 0;
        for (JsonNode item : items) {
            if (item.get("citas_resueltas").isEmpty()) {
                continue;
            }
            Company company = byActor.get(item.get("actor").asString());
            Optional<ProjectId> project = projectOf(item.get("pregunta").asString(), company.projectByName());
            if (project.isEmpty()) {
                withoutProject++;
                continue;
            }
            total++;
            List<SearchHit> hits = hitsOf(search, company, project.get(), item);
            if (!found(hits, item, pagesByDocument)) {
                misses.add(item.get("id").asString());
            }
            problems.addAll(SupersessionCheck.problems(item.get("id").asString(), hits, supersededBy));
        }
        return new Result(total - misses.size(), total, withoutProject, misses, problems);
    }

    /** Gana el nombre más largo: «Calle Abeto 1» está contenido en «Calle Abeto 16». */
    private static Optional<ProjectId> projectOf(String question, Map<String, ProjectId> projectByName) {
        return projectByName.entrySet().stream().filter(e -> question.contains(e.getKey()))
                .max(Comparator.comparingInt(e -> e.getKey().length())).map(Map.Entry::getValue);
    }

    private static List<SearchHit> hitsOf(PostgresKnowledgeSearch search, Company company, ProjectId project,
            JsonNode item) {
        SearchQuery query = new SearchQuery(List.of(item.get("pregunta").asString()), CompanyScope.of(company.id()),
                company.visibility(), Optional.of(project), LIMIT, Db.NOBODY);
        return search.search(query).hits();
    }

    private static boolean found(List<SearchHit> hits, JsonNode item, Map<String, Map<Integer, String>> pagesByDocument) {
        List<String> expected = expectedPages(item, pagesByDocument);
        return hits.stream().anyMatch(h -> expected.contains(h.title() + "#" + h.pageNumber().orElse(0)));
    }

    private static List<String> expectedPages(JsonNode item, Map<String, Map<Integer, String>> pagesByDocument) {
        List<String> expected = new ArrayList<>();
        for (JsonNode cite : item.get("citas_resueltas")) {
            String document = cite.get("doc").asString();
            pagesByDocument.get(document).forEach((number, text) -> {
                if (QuoteMatcher.containsLiteral(text, cite.get("ancla").asString())) {
                    expected.add(document + "#" + number);
                }
            });
        }
        return expected;
    }

    static void report(Result result, Path thresholdFile) throws IOException {
        double percentage = 100.0 * result.hits() / result.total();
        System.out.printf(Locale.ROOT, "ARCHIVO %d/%d (%.2f%%) fallos=%s sin_proyecto=%d%n", result.hits(),
                result.total(), percentage, result.misses(), result.withoutProject());
        if (!Files.exists(thresholdFile)) {
            Files.writeString(thresholdFile, String.format(Locale.ROOT, "%.2f%n", Math.floor(percentage * 100) / 100));
        }
        double threshold = Double.parseDouble(Files.readString(thresholdFile).trim());
        assertThat(percentage).as("aciertos %d/%d, fallos %s", result.hits(), result.total(), result.misses())
                .isGreaterThanOrEqualTo(threshold);
        assertThat(result.problems()).as("problemas de sustitución: %s", result.problems()).isEmpty();
    }
}
