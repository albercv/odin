package com.e2d.odin.domain.intake;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.intake.DuplicateFinder.Candidate;
import com.e2d.odin.domain.intake.DuplicateFinder.Duplicate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Las cifras del estudio (parte A) sobre un subconjunto del archivo revuelto (semilla 13): escaneos con precisión y
 * cobertura de 1,00, y versiones con una precisión de 0,83 o más. El proyecto de cada documento es el de la verdad del
 * estudio (en la carga real es el proyecto confirmado); las parejas verdaderas salen de {@code verdad.json}, nunca de
 * este código. Subconjunto: experimentos/2026-11-13-primera-carga/parte_a/subconjunto_duplicados.py
 */
class IntakeDuplicatesBatteryTest {

    private static final String BATTERY = "src/test/resources/intake-battery-duplicates/";

    private static List<Candidate> docs;
    private static Set<String> truthScans;
    private static Set<String> truthVersions;
    private static List<Duplicate> found;

    @BeforeAll
    static void load() throws IOException {
        docs = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(BATTERY + "docs.tsv"))) {
            String[] f = line.split("\t", -1);
            docs.add(new Candidate(f[0], f[1], f[2].equals("1"), Files.readString(Path.of(BATTERY + f[0]), StandardCharsets.UTF_8)));
        }
        truthScans = new HashSet<>();
        truthVersions = new HashSet<>();
        for (String line : Files.readAllLines(Path.of(BATTERY + "pairs.tsv"))) {
            String[] f = line.split("\t");
            (f[2].equals("escaneo") ? truthScans : truthVersions).add(key(f[0], f[1]));
        }
        found = DuplicateFinder.find(docs);
    }

    private static String key(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private static Set<String> predicted(ProposalKind kind) {
        return found.stream().filter(d -> d.kind() == kind).map(d -> key(d.documentId(), d.otherId()))
                .collect(Collectors.toSet());
    }

    @Test
    void scansAreFoundWithPrecisionAndRecallOfOne() {
        // 6 escaneos con su original; los demás documentos son versiones, plantillas de otras comunidades y sueltos
        assertThat(truthScans).hasSize(6);
        assertThat(predicted(ProposalKind.SAME_AS_SCAN)).isEqualTo(truthScans);
    }

    @Test
    void versionsHaveAPrecisionAndRecallOfAtLeast083() {
        // en el estudio, 38 de 46 (0,83); aquí 34 de 40 (0,85): los que faltan son versiones reales de cadenas sin anotar
        Set<String> predicted = predicted(ProposalKind.SUPERSEDES);
        long hits = predicted.stream().filter(truthVersions::contains).count();
        assertThat(truthVersions).hasSize(40);
        assertThat((double) hits / predicted.size()).isGreaterThanOrEqualTo(0.83);
        assertThat((double) hits / truthVersions.size()).isGreaterThanOrEqualTo(0.83);
    }

    @Test
    void everyProposalQuotesTheTextOfItsDocument() {
        for (Duplicate d : found) {
            String text = docs.stream().filter(c -> c.id().equals(d.documentId())).findFirst().orElseThrow().text();
            assertThat(text).contains(d.reason());
        }
    }

    @Test
    void nothingIsProposedBetweenDifferentProjects() {
        for (Duplicate d : found) {
            String a = docs.stream().filter(c -> c.id().equals(d.documentId())).findFirst().orElseThrow().project();
            String b = docs.stream().filter(c -> c.id().equals(d.otherId())).findFirst().orElseThrow().project();
            assertThat(a).isEqualTo(b);
        }
    }

    @Test
    void theResultIsTheSameInEveryRun() {
        assertThat(DuplicateFinder.find(docs)).isEqualTo(found);
    }
}
