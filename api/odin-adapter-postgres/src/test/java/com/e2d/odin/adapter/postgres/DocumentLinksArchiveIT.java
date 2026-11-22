package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.citation.LinkDocuments;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Sobre el archivo ficticio: salen los vínculos de las sustituciones que se expresan con un código, y ninguno más. */
class DocumentLinksArchiveIT extends AbstractPostgresIT {

    private static final Pattern POLICY_CODE = Pattern.compile("\\b[A-Z]{2,4}-\\d{3,5}-\\d{4}\\b");

    @Test
    void detectedLinksAreExactlyTheCodedSupersessionsOfTheManifest() throws IOException {
        JsonNode manifest = JsonMapper.builder().build().readTree(Files.readString(ArchiveBattery.DIR.resolve("manifiesto.json")));
        ArchiveLoader loader = new ArchiveLoader(this);
        Map<String, Map<Integer, String>> pages = new LinkedHashMap<>();
        Map<String, String> pairs = new LinkedHashMap<>();
        java.util.List<CompanyId> companies = new java.util.ArrayList<>();
        for (JsonNode entry : manifest.get("empresas")) {
            String key = entry.get("clave").asString();
            CompanyId id = newCompany();
            UserId actor = newUser(id);
            pages.putAll(loader.load(id, actor, ArchiveBattery.DIR.resolve("corpus"),
                    ArchiveBattery.folderNames(manifest, key)).pagesByDocument());
            companies.add(id);
        }
        for (CompanyId id : companies) {
            pairs.putAll(loader.applySupersessions(id, ArchiveBattery.DIR.resolve("claves.json")));
        }
        LinkDocuments linker = new LinkDocuments(new PostgresDocumentLinkRepository(tx), Clock.fixed(NOW, ZoneOffset.UTC), 200);
        while (linker.processBatch() > 0) {
            // hasta analizar todo el archivo
        }

        Set<String> expected = codedPairs(pairs, pages);
        Set<String> detected = detectedReplacements();
        int allLinks = JdbcClient.create(Db.owner()).sql("SELECT count(*) FROM document_link").query(Integer.class).single();
        System.out.printf("VINCULOS parejas del manifiesto=%d con código (detectadas)=%d sin código (no detectadas)=%d "
                + "vínculos totales=%d%n", pairs.size(), expected.size(), pairs.size() - expected.size(), allLinks);

        assertThat(detected).isEqualTo(expected);
        assertThat(allLinks).as("vínculos que no son una pareja del manifiesto con código").isEqualTo(expected.size());
        assertThat(expected).isNotEmpty();
    }

    private static Set<String> detectedReplacements() {
        return new LinkedHashSet<>(JdbcClient.create(Db.owner()).sql("""
                SELECT t.title || ' -> ' || s.title FROM document_link l JOIN document s ON s.id = l.source_document_id
                JOIN document t ON t.id = l.target_document_id WHERE l.relation = 'REPLACES'""")
                .query(String.class).list());
    }

    /** Vieja -> vigente de las parejas cuyo documento vigente cita en su texto el código de la póliza vieja. */
    private static Set<String> codedPairs(Map<String, String> pairs, Map<String, Map<Integer, String>> pages) {
        Set<String> coded = new LinkedHashSet<>();
        pairs.forEach((old, current) -> {
            Matcher own = POLICY_CODE.matcher(pages.get(old).get(1));
            if (own.find() && String.join("\n", pages.get(current).values()).contains(own.group())) {
                coded.add(old + " -> " + current);
            }
        });
        return coded;
    }
}
