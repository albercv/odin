package com.e2d.odin.application.intake;

import com.e2d.odin.domain.document.DocumentEntry;

/** Qué pasó con un fichero subido al lote y el documento al que corresponde. */
public record IntakeFileResult(Outcome outcome, DocumentEntry entry) {

    public enum Outcome {
        /** Entró como documento nuevo, pendiente. */
        CREATED,
        /** La huella ya estaba en la empresa (o en el lote): no se creó nada. */
        ALREADY_THERE,
        /** Se descartó en este lote: no vuelve a entrar al reanudar. */
        DISCARDED_IN_BATCH
    }
}
