package com.e2d.odin.domain.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Pruebas unitarias de textlib.py (fechas, importes, identificadores y tipo), en JUnit. */
class DocumentFactsTest {

    @Test
    void datesInBothFormats() {
        assertThat(DocumentFacts.literals("Fecha: 12 de febrero de 2025 y el 3/4/2024."))
                .containsOnlyKeys("d20250212", "d20240403")
                .containsEntry("d20250212", "12 de febrero de 2025").containsEntry("d20240403", "3/4/2024");
    }

    @Test
    void amountsWithoutSpacesOrThousandDots() {
        assertThat(DocumentFacts.literals("Prima de 1.830 euros, franquicia de 300 euros y 45,50 €."))
                .containsOnlyKeys("a1830euros", "a300euros", "a45,50euros").containsEntry("a1830euros", "1.830 euros");
    }

    @Test
    void ownAndReplacedPolicyIds() {
        String text = "Póliza nº ATL-3887-2025\nEsta póliza sustituye a la póliza nº ATL-1194-2023, que queda sin efecto.";
        assertThat(DocumentFacts.ownId(text)).contains("ATL-3887-2025");
        assertThat(DocumentFacts.replacedId(text)).contains("ATL-1194-2023");
        assertThat(DocumentFacts.replacedId("Póliza nº ATL-1194-2023")).isEqualTo(Optional.empty());
    }

    @Test
    void typeFromTheFirstLines() {
        assertThat(DocumentFacts.guessType("ACTA DE LA JUNTA\nCOMUNIDAD")).isEqualTo("acta");
        assertThat(DocumentFacts.guessType("CONDICIONES DEL SEGURO")).isEqualTo("poliza");
        assertThat(DocumentFacts.guessType("Hola")).isEqualTo("desconocido");
    }
}
