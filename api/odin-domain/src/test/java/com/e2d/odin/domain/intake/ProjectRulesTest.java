package com.e2d.odin.domain.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Las reglas de descubrimiento del estudio (clasificar.py y test_clasificar.py), en Java. */
class ProjectRulesTest {

    // Subconjunto del archivo revuelto (semilla 13): experimentos/2026-11-13-primera-carga/parte_b/subconjunto_descubrimiento.py
    private static final String BATTERY = "src/test/resources/intake-battery/";

    @Test
    void normalizeDropsAccentsSignsAndPageMarks() {
        assertThat(ProjectRules.normalize("=== Página 2 ===\nCALLE Castaño, 42 (bloque)")).isEqualTo("calle castano 42 bloque");
        assertThat(ProjectRules.normalize("  a \n\t b  ")).isEqualTo("a b");
    }

    @Test
    void discoverFindsTheNamesByTheStudyPatterns() {
        String text = "ACTA\nCOMUNIDAD DE PROPIETARIOS RESIDENCIAL LOS NARANJOS 2\n"
                + "Comunidad: Calle Olmo 24 (bloque 2), Puerto Albar";

        assertThat(ProjectRules.discover(text)).contains("calle olmo 24 bloque 2", "residencial los naranjos 2");
    }

    @Test
    void discoverFindsTheRiskAddressAndCutsTheSentenceTail() {
        assertThat(ProjectRules.discover("Domicilio del riesgo: Avenida del Puerto 3, Puerto Albar"))
                .containsExactly("avenida del puerto 3");
        assertThat(ProjectRules.discover("Comunidad de Propietarios Calle Haya 2 se reunió el lunes"))
                .containsExactly("calle haya 2");
    }

    @Test
    void discoverClosesAnOpenBracket() {
        assertThat(ProjectRules.discover("Comunidad: Calle Olmo 24 (bloque 2")).containsExactly("calle olmo 24 bloque 2");
    }

    @Test
    void discoverFindsNothingInTextWithoutAPlace() {
        assertThat(ProjectRules.discover("Norma interna de archivo de documentos")).isEmpty();
    }

    @Test
    void discoverCountsEachDocumentOnceAndKeepsTheFirstSpelling() {
        List<ProjectRules.Discovered> found = ProjectRules.discover(List.of(
                "Comunidad: Calle Haya 2\nComunidad: Calle Haya 2", "Comunidad: CALLE HAYA 2", "nada"));

        assertThat(found).containsExactly(new ProjectRules.Discovered("Calle Haya 2", 2));
    }

    private static final List<String> LIST = List.of("Calle Olmo 24", "Calle Olmo 24 (bloque 2)", "Residencial Las Acacias",
            "Residencial Las Acacias 2", "Avenida del Puerto 3", "Calle Haya 2");

    private static ProjectMatch match(String text) {
        return ProjectRules.match(text, LIST, Optional.empty());
    }

    @Test
    void aSingleProjectIsHighWithItsQuote() {
        ProjectMatch m = match("Comunidad de Propietarios Calle Haya 2, Puerto Albar");

        assertThat(m.project()).contains("Calle Haya 2");
        assertThat(m.confidence()).isEqualTo(ProposalConfidence.HIGH);
        assertThat(m.source()).isEqualTo(ProposalSource.RULES);
        assertThat(m.reason()).isEqualTo("Comunidad de Propietarios Calle Haya 2, Puerto Albar");
    }

    @Test
    void theLongestNameWinsOverItsTwin() {
        assertThat(match("Calle Olmo 24 (bloque 2), Puerto Albar").project()).contains("Calle Olmo 24 (bloque 2)");
        assertThat(match("Calle Olmo 24, Puerto Albar").project()).contains("Calle Olmo 24");
        assertThat(match("Residencial Las Acacias 2").project()).contains("Residencial Las Acacias 2");
        assertThat(match("Residencial Las Acacias").project()).contains("Residencial Las Acacias");
        assertThat(match("Calle Olmo 24 (bloque 2)").confidence()).isEqualTo(ProposalConfidence.HIGH);
    }

    @Test
    void aNumberIsNotThePrefixOfAnother() {
        assertThat(match("Calle Haya 25").project()).isEmpty();
        assertThat(match("Calle Haya 25").candidates()).isEmpty();
    }

    @Test
    void theNameVariantsAreRecognised() {
        assertThat(match("C/ Olmo, 24").project()).contains("Calle Olmo 24");
        assertThat(match("Res. Las Acacias 2").project()).contains("Residencial Las Acacias 2");
        assertThat(match("Avda. del Puerto 3").project()).contains("Avenida del Puerto 3");
    }

    @Test
    void theHeaderBreaksATieButStaysLow() {
        ProjectMatch m = match("COMUNIDAD CALLE HAYA 2\n\nlinea\n\notra\n\nver Calle Olmo 24 y Calle Olmo 24");

        assertThat(m.project()).contains("Calle Haya 2");
        assertThat(m.candidates()).containsExactly("Calle Haya 2", "Calle Olmo 24");
        assertThat(m.confidence()).isEqualTo(ProposalConfidence.LOW);
        assertThat(m.reason()).isEqualTo("COMUNIDAD CALLE HAYA 2");
    }

    @Test
    void severalWithoutATieBreakAreLowWithAllTheCandidates() {
        ProjectMatch m = match("Calle Haya 2 y Calle Olmo 24");

        assertThat(m.project()).isEmpty();
        assertThat(m.candidates()).containsExactlyInAnyOrder("Calle Haya 2", "Calle Olmo 24");
        assertThat(m.confidence()).isEqualTo(ProposalConfidence.LOW);
    }

    @Test
    void textWithoutAProjectProposesNothing() {
        ProjectMatch m = match("Norma interna de custodia de llaves");

        assertThat(m.project()).isEmpty();
        assertThat(m.candidates()).isEmpty();
        assertThat(m.confidence()).isEqualTo(ProposalConfidence.LOW);
    }

    @Test
    void theQuoteHasAtMost300Characters() {
        ProjectMatch m = match("Calle Haya 2" + " relleno".repeat(100));

        assertThat(m.project()).contains("Calle Haya 2");
        assertThat(m.reason()).hasSize(300).startsWith("Calle Haya 2 relleno");
    }

    @Test
    void aFolderNamedLikeTheProjectAddsItsSignal() {
        ProjectMatch agrees = ProjectRules.match("Calle Haya 2, Puerto Albar", LIST, Optional.of("Clientes/Calle Haya 2"));
        ProjectMatch alone = ProjectRules.match("Carta sin lugar", LIST, Optional.of("Clientes/C. Haya 2"));
        ProjectMatch breaks = ProjectRules.match("Calle Haya 2 y Calle Olmo 24", LIST, Optional.of("Calle Olmo 24"));

        assertThat(agrees.project()).contains("Calle Haya 2");
        assertThat(agrees.confidence()).isEqualTo(ProposalConfidence.HIGH);
        assertThat(alone.project()).contains("Calle Haya 2");
        assertThat(alone.source()).isEqualTo(ProposalSource.FOLDER);
        assertThat(alone.confidence()).isEqualTo(ProposalConfidence.HIGH);
        assertThat(alone.reason()).isEqualTo("Clientes/C. Haya 2");
        assertThat(breaks.project()).contains("Calle Olmo 24");
        assertThat(breaks.source()).isEqualTo(ProposalSource.FOLDER);
    }

    @Test
    void aFolderThatContradictsTheTextIsLowWithBothCandidates() {
        ProjectMatch m = ProjectRules.match("Calle Haya 2, Puerto Albar", LIST, Optional.of("Calle Olmo 24"));

        assertThat(m.confidence()).isEqualTo(ProposalConfidence.LOW);
        assertThat(m.project()).contains("Calle Haya 2");
        assertThat(m.candidates()).containsExactly("Calle Haya 2", "Calle Olmo 24");
        assertThat(m.source()).isEqualTo(ProposalSource.RULES);
    }

    @Test
    void aFolderThatIsNoProjectIsIgnored() {
        ProjectMatch m = ProjectRules.match("Calle Haya 2", LIST, Optional.of("Varios"));

        assertThat(m.confidence()).isEqualTo(ProposalConfidence.HIGH);
        assertThat(m.project()).contains("Calle Haya 2");
    }

    @Test
    void theStudyBatterySubsetGivesTheCommunitiesWithNoneExtra() throws IOException {
        List<String> texts = new ArrayList<>();
        for (String file : Files.readAllLines(Path.of(BATTERY + "index.txt"))) {
            texts.add(Files.readString(Path.of(BATTERY + file), StandardCharsets.UTF_8));
        }
        Set<String> expected = new TreeSet<>(Files.readAllLines(Path.of(BATTERY + "expected.txt")));
        Set<String> found = new TreeSet<>();
        ProjectRules.discover(texts).forEach(d -> found.add(ProjectRules.normalize(d.name())));

        // en el estudio, 60 de 61 y ninguna de más; falta la única que no es una comunidad
        assertThat(expected).hasSize(61);
        assertThat(found).hasSize(60).isSubsetOf(expected);
        expected.removeAll(found);
        assertThat(expected).containsExactly("procedimientos generales");
    }
}
