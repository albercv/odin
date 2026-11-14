package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.shared.CompanyId;

/** Termina la subida: el lote pasa a READING y la lectura sigue por las cadenas de siempre. */
public class CloseIntakeUpload {

    private final IntakeRepository intake;

    public CloseIntakeUpload(IntakeRepository intake) {
        this.intake = intake;
    }

    public IntakeBatch execute(Actor actor, CompanyId companyId, IntakeBatchId batchId) {
        IntakeBatch reading = IntakeBatches.require(intake, actor, companyId, batchId).startReading();
        intake.save(reading);
        return reading;
    }
}
