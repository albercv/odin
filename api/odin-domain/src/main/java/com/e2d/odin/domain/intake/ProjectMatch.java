package com.e2d.odin.domain.intake;

import java.util.List;
import java.util.Optional;

/**
 * Resultado puro de asignar un texto a un proyecto de la lista: el proyecto propuesto (vacío si no hay uno o hay
 * varios sin desempate), todos los candidatos, la confianza, la fuente y la cita literal que lo justifica (D-008).
 */
public record ProjectMatch(Optional<String> project, List<String> candidates, ProposalConfidence confidence,
        ProposalSource source, String reason) {
}
