package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cada expediente del archivo ficticio, buscado por las 6 formas de su número: salen su ficha y un documento suyo. */
class CaseNumberBatteryIT extends AbstractPostgresIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern CASE_NUMBER = Pattern.compile("caso-(\\d{4})-(\\d+)-");

    @Test
    void everyWrittenFormOfACaseNumberFindsTheCaseCardAndADocument() throws IOException {
        JsonNode manifest = JSON.readTree(Files.readString(ArchiveBattery.DIR.resolve("manifiesto.json")));
        JsonNode cases = JSON.readTree(Files.readString(ArchiveBattery.DIR.resolve("expedientes.json")));
        Map<String, ArchiveBattery.Company> companies = load(manifest, cases);
        PostgresKnowledgeSearch search = new PostgresKnowledgeSearch(tx);
        List<String> misses = new ArrayList<>();
        int total = 0;
        for (JsonNode c : cases) {
            ArchiveBattery.Company company = companies.get(c.get("caso").asString().split("/")[0]);
            for (String form : forms(c)) {
                total++;
                int rank = missRank(search, company, c, form);
                if (rank != 0) {
                    misses.add(c.get("caso").asString() + " «" + form + "»");
                }
            }
        }
        String report = String.format(Locale.ROOT, "NUMEROS-CASO %d/%d fallos=%s%n", total - misses.size(), total, misses);
        System.out.print(report);
        Files.writeString(Path.of("target/case-number-battery.txt"), report);
        assertThat(misses).as("fallos").isEmpty();
        assertThat(total).isEqualTo(72);
    }

    /** 0 si salen la ficha y algún documento entre los 10 primeros; si no, 1. */
    private int missRank(PostgresKnowledgeSearch search, ArchiveBattery.Company company, JsonNode c, String form) {
        List<SearchHit> hits = search.search(new SearchQuery(List.of(form), CompanyScope.of(company.id()),
                company.visibility(), Optional.empty(), 10, Db.NOBODY)).hits();
        List<String> titles = hits.stream().map(SearchHit::title).toList();
        boolean card = titles.contains(c.get("caso").asString());
        boolean document = false;
        for (JsonNode d : c.get("documentos")) {
            document |= titles.contains(d.asString());
        }
        return card && document ? 0 : 1;
    }

    private static List<String> forms(JsonNode c) {
        Matcher m = CASE_NUMBER.matcher(c.get("caso").asString());
        assertThat(m.find()).isTrue();
        List<String> forms = new ArrayList<>(List.of(m.group(1) + "-" + m.group(2), m.group(1) + "/" + m.group(2)));
        c.get("formas_numero").forEach(f -> forms.add(f.asString()));
        return forms;
    }

    /** El corpus (donde están las fichas de caso) y las carpetas de los expedientes, en las mismas empresas. */
    private Map<String, ArchiveBattery.Company> load(JsonNode manifest, JsonNode cases) throws IOException {
        List<String> caseKeys = new ArrayList<>();
        cases.forEach(c -> caseKeys.add(c.get("caso").asString()));
        Map<String, ArchiveBattery.Company> companies = new LinkedHashMap<>();
        for (JsonNode company : manifest.get("empresas")) {
            String key = company.get("clave").asString();
            CompanyId id = newCompany();
            UserId actor = newUser(id);
            ArchiveLoader loader = new ArchiveLoader(this);
            Map<String, String> corpusNames = ArchiveBattery.folderNames(manifest, key);
            LoadedArchive corpus = loader.load(id, actor, ArchiveBattery.DIR.resolve("corpus"), corpusNames,
                    caseKeys::contains);
            Map<String, String> names = folders(manifest, key);
            LoadedArchive files = loader.load(id, actor, ArchiveBattery.DIR.resolve("expedientes"), names);
            // Resultado descartado: solo da de alta al actor en los proyectos del corpus (los de expedientes comparten nombre).
            ArchiveBattery.companyOf(this, id, actor, corpus, corpusNames);
            companies.put(key, ArchiveBattery.companyOf(this, id, actor, files, names));
        }
        return companies;
    }

    private static Map<String, String> folders(JsonNode manifest, String company) throws IOException {
        Map<String, String> names = new LinkedHashMap<>();
        Path base = ArchiveBattery.DIR.resolve("expedientes").resolve(company);
        try (Stream<Path> dirs = Files.list(base)) {
            dirs.sorted().forEach(d -> names.put(company + "/" + d.getFileName(), null));
        }
        for (JsonNode p : manifest.get("proyectos")) {
            String folder = p.get("empresa").asString() + "/" + p.get("clave").asString();
            if (names.containsKey(folder)) {
                names.put(folder, p.get("nombre").asString());
            }
        }
        return names;
    }
}
