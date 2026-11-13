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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Q153: la norma sin proyecto «albar/_general/norma-convocatoria-juntas.txt» p. 1. Repite sin IA cada juego de consultas
 * que guardó la IA y anota en qué puesto sale con y sin {@code scope: general}. Solo comprueba invariantes, no puestos.
 */
class GeneralScopeRuleIT extends AbstractPostgresIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String RULE = "albar/_general/norma-convocatoria-juntas.txt";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    @Test
    void ranksOfTheRuleWithAndWithoutGeneralScope() throws IOException {
        ArchiveBattery.Company albar = loadArchive();
        PostgresKnowledgeSearch search = new PostgresKnowledgeSearch(tx);
        JsonNode sets = JSON.readTree(Files.readString(Path.of("src/test/resources/q153/consultas-q153.json")));
        StringBuilder table = new StringBuilder("| # | Consultas | IA límite 10 | IA límite 50 | general límite 10"
                + " | general límite 50 |\n|---|---|---|---|---|---|\n");
        int[][] top = new int[4][2];
        int n = 0;
        for (JsonNode set : sets) {
            List<String> queries = new ArrayList<>();
            set.forEach(q -> queries.add(q.asString()));
            table.append("| ").append(++n).append(" | ").append(String.join(" / ", queries).replace("|", "\\|"));
            for (int column = 0; column < 4; column++) {
                int rank = rank(search(search, albar, queries, column % 2 == 0 ? DEFAULT_LIMIT : MAX_LIMIT, column > 1));
                table.append(" | ").append(rank == 0 ? "no sale" : rank);
                top[column][0] += rank >= 1 && rank <= 5 ? 1 : 0;
                top[column][1] += rank >= 1 && rank <= 10 ? 1 : 0;
            }
            table.append(" |\n");
        }
        table.append("\nEn los 5 primeros / en los 10 primeros, de ").append(n).append(" juegos: ");
        for (int column = 0; column < 4; column++) {
            table.append(top[column][0]).append(" / ").append(top[column][1]).append(column < 3 ? "; " : "\n");
        }
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/q153-ranks.md"), table.toString());
        System.out.println("Q153 puestos de la norma (" + RULE + " p. 1)\n" + table);
    }

    private ArchiveBattery.Company loadArchive() throws IOException {
        JsonNode manifest = JSON.readTree(Files.readString(ArchiveBattery.DIR.resolve("manifiesto.json")));
        ArchiveLoader loader = new ArchiveLoader(this);
        ArchiveBattery.Company albar = null;
        List<CompanyId> companies = new ArrayList<>();
        for (JsonNode c : manifest.get("empresas")) {
            String key = c.get("clave").asString();
            Map<String, String> names = ArchiveBattery.folderNames(manifest, key);
            CompanyId company = newCompany();
            UserId actor = newUser(company);
            companies.add(company);
            LoadedArchive loaded = loader.load(company, actor, ArchiveBattery.DIR.resolve("corpus"), names);
            if (key.equals("albar")) {
                assertThat(loaded.pagesByDocument()).containsKey(RULE);
                albar = ArchiveBattery.companyOf(this, company, actor, loaded, names);
            }
        }
        ArchiveBattery.applySupersessions(loader, companies);
        return albar;
    }

    private static List<SearchHit> search(PostgresKnowledgeSearch search, ArchiveBattery.Company company,
            List<String> queries, int limit, boolean general) {
        List<SearchHit> hits = search.search(new SearchQuery(queries, CompanyScope.of(company.id()),
                company.visibility(), Optional.empty(), limit, Db.NOBODY, Optional.empty(), general)).hits();
        if (general) {
            assertThat(hits).allSatisfy(h -> assertThat(h.projectId()).isEmpty());
        }
        return hits;
    }

    /** Puesto (desde 1) de la página 1 de la norma, o 0 si no sale. */
    private static int rank(List<SearchHit> hits) {
        for (int i = 0; i < hits.size(); i++) {
            if (RULE.equals(hits.get(i).title()) && hits.get(i).pageNumber().orElse(0) == 1) {
                return i + 1;
            }
        }
        return 0;
    }
}
