package com.e2d.odin.domain.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Las reglas de ficha de caso y de tipo del estudio (test_clasificar.py), en Java. */
class CaseFileRulesTest {

    @Test
    void theNumberOnItsOwnLine() {
        CaseFileRules.CaseMark m = CaseFileRules.detect("ADMINISTRACIONES RIBERA\nFICHA DE CASO\n\nCaso 2024-342\nX");

        assertThat(m.caseFile()).isTrue();
        assertThat(m.number()).contains("2024-342");
        assertThat(m.reason()).isEqualTo("FICHA DE CASO");
    }

    @Test
    void theNumberWithALabel() {
        CaseFileRules.CaseMark m = CaseFileRules.detect(
                "ADMINISTRACIONES RIBERA · FICHA DE CASO\nX\nNúmero de caso: Caso 2025-006\n");

        assertThat(m.caseFile()).isTrue();
        assertThat(m.number()).contains("2025-006");
    }

    @Test
    void aMeetingMinutesThatMentionsACaseIsNotACaseFile() {
        CaseFileRules.CaseMark m = CaseFileRules.detect("ACTA DE LA JUNTA\nse trató el caso de la piscina");

        assertThat(m.caseFile()).isFalse();
        assertThat(m.number()).isEmpty();
    }

    @Test
    void theTypeComesFromTheHeaderWords() {
        Map<String, String> cases = Map.ofEntries(
                Map.entry("ACTA DE LA JUNTA GENERAL ORDINARIA DE PROPIETARIOS\nX", "acta"),
                Map.entry("ADMINISTRACIONES RIBERA\nFICHA DE CASO\n", "caso"),
                Map.entry("ANEXO Nº 1 AL CONTRATO DE MANTENIMIENTO DE ASCENSOR", "anexo"),
                Map.entry("CONTRATO DE SERVICIO DE LIMPIEZA", "contrato"),
                Map.entry("SEGURO MULTIRRIESGO PARA COMUNIDADES\nCONDICIONES PARTICULARES", "poliza"),
                Map.entry("CONDICIONES PARTICULARES DEL SEGURO", "poliza"),
                Map.entry("PRESUPUESTO ANUAL DE GASTOS 2026", "presupuesto"),
                Map.entry("COMUNICACIÓN A LOS PROPIETARIOS", "comunicacion"),
                Map.entry("AVISO DE CORTE PROGRAMADO", "aviso"),
                Map.entry("NORMA INTERNA DE CUSTODIA DE LLAVES", "norma"),
                Map.entry("PROCEDIMIENTO GENERAL DE MUDANZAS", "procedimiento"),
                Map.entry("ESTATUTOS DE LA COMUNIDAD DE PROPIETARIOS CALLE OLMO 4", "estatutos"));

        cases.forEach((text, type) -> assertThat(CaseFileRules.documentType(text)).as(text).contains(type));
    }

    @Test
    void anUnknownHeaderHasNoType() {
        assertThat(CaseFileRules.documentType("Hola, esto es una carta")).isEmpty();
    }
}
