package com.e2d.odin.domain.organization;

/** Un proyecto con cuántos miembros tiene, calculado en la misma consulta que la lista. */
public record ProjectSummary(Project project, int memberCount) {
}
