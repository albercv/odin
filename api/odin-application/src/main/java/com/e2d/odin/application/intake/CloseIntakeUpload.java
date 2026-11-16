package com.e2d.odin.application.intake;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.shared.CompanyId;

/** Termina la subida: el lote pasa a READING y la lectura sigue por las cadenas de siempre. */
public class CloseIntakeUpload {

    private final IntakeRepository intake;
    private final AuditLog audit;

    public CloseIntakeUpload(IntakeRepository intake, AuditLog audit) {
        this.intake = intake;
        this.audit = audit;
    }

    public IntakeBatch execute(Actor actor, CompanyId companyId, IntakeBatchId batchId) {
        IntakeBatch reading = IntakeBatches.require(intake, actor, companyId, batchId).startReading();
        intake.save(reading);
        audit.record(AuditSupport.onIntake(AuditEntry.by(actor, AuditAction.INTAKE_UPLOAD_CLOSED), companyId, batchId));
        return reading;
    }
}
