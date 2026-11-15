package com.e2d.odin.application.intake;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.application.document.UploadDocument;
import com.e2d.odin.application.document.UploadOutcome;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;

/** Sube un fichero al lote con {@link UploadDocument}: nace pendiente, sin proyecto y con su ruta. Reintentar no duplica. */
public class AddFileToIntake {

    private final IntakeRepository intake;
    private final UploadDocument upload;
    private final AuditLog audit;

    public AddFileToIntake(IntakeRepository intake, UploadDocument upload, AuditLog audit) {
        this.intake = intake;
        this.upload = upload;
        this.audit = audit;
    }

    public IntakeFileResult execute(Actor actor, CompanyId companyId, IntakeBatchId batchId, String path,
            String filename, byte[] content) {
        IntakeBatch batch = IntakeBatches.require(intake, actor, companyId, batchId);
        if (batch.state() != IntakeState.OPEN) {
            throw new ConflictException("El lote ya no admite más ficheros");
        }
        String cleanPath = Validation.requireText(path, "la ruta del fichero");
        UploadOutcome outcome = upload.executeForIntake(actor, companyId, batchId, cleanPath, filename, filename,
                content);
        audit.record(outcome.auditEntry(actor, content.length).with("intakeBatchId", batchId.value().toString()));
        if (!outcome.duplicate()) {
            return new IntakeFileResult(IntakeFileResult.Outcome.CREATED, outcome.entry());
        }
        boolean discarded = outcome.document().withdrawn();
        intake.countFile(companyId, batchId, discarded);
        return new IntakeFileResult(discarded ? IntakeFileResult.Outcome.DISCARDED_IN_BATCH
                : IntakeFileResult.Outcome.ALREADY_THERE, outcome.entry());
    }
}
