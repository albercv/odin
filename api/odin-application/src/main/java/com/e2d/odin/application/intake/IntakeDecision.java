package com.e2d.odin.application.intake;

/** Lo que el administrador decide de una propuesta, una a una. */
public enum IntakeDecision {
    /** Hacer lo que la propuesta dice. */
    CONFIRM,
    /** Otro proyecto que el propuesto (con {@code projectId}). */
    CORRECT_PROJECT,
    /** El documento queda sin proyecto, visible para toda la empresa. */
    COMPANY_WIDE,
    /** Dejarlo pendiente y oculto, sin plazo (D-080): queda quién y cuándo, y se puede decidir después. */
    DONT_KNOW,
    REJECT
}
