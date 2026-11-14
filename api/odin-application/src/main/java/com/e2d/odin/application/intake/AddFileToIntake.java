package com.e2d.odin.application.intake;

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

    public AddFileToIntake(IntakeRepository intake, UploadDocument upload) {
        this.intake = intake;
        this.upload = upload;
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
        if (!outcome.duplicate()) {
            return new IntakeFileResult(IntakeFileResult.Outcome.CREATED, outcome.entry());
        }
        return new IntakeFileResult(outcome.document().withdrawn() ? IntakeFileResult.Outcome.DISCARDED_IN_BATCH
                : IntakeFileResult.Outcome.ALREADY_THERE, outcome.entry());
    }
}
