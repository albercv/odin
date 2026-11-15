package com.e2d.odin.domain.intake;

/**
 * Estado de una propuesta o de un candidato: nada cambia hasta que una persona lo decide (D2). DONT_KNOW («no lo sé»)
 * es solo de las propuestas: queda dicho quién y cuándo, y la propuesta sigue abierta y se puede decidir después.
 */
public enum DecisionState { PROPOSED, CONFIRMED, REJECTED, DONT_KNOW }
