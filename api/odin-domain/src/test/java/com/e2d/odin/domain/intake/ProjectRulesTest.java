package com.e2d.odin.domain.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
