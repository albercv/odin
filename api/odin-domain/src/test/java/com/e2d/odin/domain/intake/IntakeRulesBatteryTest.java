package com.e2d.odin.domain.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Las cifras del estudio (parte B) sobre el subconjunto del archivo revuelto (semilla 13): con la lista confirmada, todo
 * lo que sale con confianza alta es el proyecto correcto, y el tipo, la ficha y el número de caso aciertan siempre.
 */
class IntakeRulesBatteryTest {

    private static final String BATTERY = "src/test/resources/intake-battery/";

    private record Doc(String text, String project, String type, String caseNumber) {
    }

    private static final String GENERAL = "procedimientos generales";

    private static List<String> projects;
    private static List<Doc> docs;

    @BeforeAll
    static void load() throws IOException {
        projects = Files.readAllLines(Path.of(BATTERY + "expected.txt"));
        docs = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(BATTERY + "truth.tsv"))) {
            String[] f = line.split("\t", -1);
            docs.add(new Doc(Files.readString(Path.of(BATTERY + f[0]), StandardCharsets.UTF_8), f[1], f[2], f[3]));
        }
    }

    @Test
    void everyProjectProposedWithHighConfidenceIsRight() {
        int high = 0;
        for (Doc d : docs) {
            ProjectMatch m = ProjectRules.match(d.text(), projects, Optional.empty());
            if (m.confidence() == ProposalConfidence.HIGH) {
                high++;
                assertThat(m.project()).as(d.text().lines().findFirst().orElse("")).contains(d.project());
            }
        }

        // en el estudio, 810 de 810 con confianza alta; el resto no nombra ningún proyecto: 4 sin proyecto y los 2
        // procedimientos de la empresa, cuyo «proyecto» no es una comunidad (el único que el descubrimiento no halla)
        assertThat(docs).hasSize(126);
        assertThat(high).isEqualTo(120);
    }

    @Test
    void theDocumentsThatNameNoCommunityProposeNone() {
        List<Doc> none = docs.stream().filter(d -> d.project().isEmpty() || d.project().equals(GENERAL)).toList();

        assertThat(none).hasSize(6);
        none.forEach(d -> assertThat(ProjectRules.match(d.text(), projects, Optional.empty()).project()).isEmpty());
    }

    @Test
    void theTypeTheCaseFileAndTheCaseNumberAreAlwaysRight() {
        int cases = 0;
        for (Doc d : docs) {
            CaseFileRules.CaseMark mark = CaseFileRules.detect(d.text());
            assertThat(CaseFileRules.documentType(d.text())).as(d.text().lines().findFirst().orElse("")).contains(d.type());
            assertThat(mark.caseFile()).isEqualTo(d.type().equals("caso"));
            assertThat(mark.number().orElse("")).isEqualTo(d.caseNumber());
            cases += mark.caseFile() ? 1 : 0;
        }

        assertThat(cases).isEqualTo(33);
    }
}
