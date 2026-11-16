package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.IntakeCounts;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.time.Instant;

/** Lo común de los casos de uso del lote: el administrador de la empresa y un lote que es de esa empresa. */
final class IntakeBatches {

    private IntakeBatches() {
    }

    static IntakeBatch require(IntakeRepository intake, Actor actor, CompanyId companyId, IntakeBatchId batchId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        return intake.find(companyId, batchId).orElseThrow(() -> new NotFoundException("Lote no encontrado"));
    }

    /** Importar, descubrir y decidir candidatos: solo mientras la lista no esté terminada ni el lote clasificado. */
    static IntakeBatch requireListOpen(IntakeRepository intake, Actor actor, CompanyId companyId,
            IntakeBatchId batchId) {
        IntakeBatch batch = require(intake, actor, companyId, batchId);
        if (batch.projectListClosed() || batch.state() == IntakeState.IN_REVIEW
                || batch.state() == IntakeState.CLOSED) {
            throw new ConflictException("La lista de proyectos ya está terminada: no se puede cambiar");
        }
        return batch;
    }

    /** Los recuentos del lote con la definición de «leyendo» de la primera carga: sin lector, lo escaneado espera. */
    static IntakeCounts counts(DocumentRepository documents, InstallationSettings installation, CompanyId companyId,
            IntakeBatchId batchId) {
        IntakeCounts counts = documents.intakeCounts(companyId, batchId);
        return installation.scannedDocumentsSupported() ? counts : counts.withoutReader();
    }

    /** Sin documentos pendientes ni propuestas por decidir, el lote en revisión se cierra. Uno ya cerrado se deja como está. */
    static void closeIfDone(IntakeRepository intake, DocumentRepository documents, IntakeBatch batch, Instant now) {
        if (batch.state() != IntakeState.IN_REVIEW) {
            return;
        }
        boolean pending = documents.intakeCounts(batch.companyId(), batch.id()).pending() > 0;
        if (!pending && intake.proposals(batch.companyId(), batch.id()).stream()
                .noneMatch(IntakeProposal::open)) {
            intake.save(batch.close(now));
        }
    }

    /** En cola o leyéndose: lo reclamará un lector. Sin lector OCR, lo que espera al lector no está en ninguna cola. */
    static boolean beingRead(Document d, InstallationSettings installation) {
        return switch (d.status()) {
            case RECEIVED, PROCESSING, READER_PROCESSING -> true;
            case PENDING_READER -> installation.scannedDocumentsSupported();
            case READY, FAILED -> false;
        };
    }
}
