package com.e2d.odin.application.intake;

import com.e2d.odin.application.link.OriginalFile;
import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.application.port.OriginalFileStore;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.Optional;

/**
 * El original de un documento pendiente del lote, para que su administrador lo vea antes de decidir (D2). Solo los
 * pendientes de ese lote y esa empresa: un confirmado se abre desde Documentos, y a un trabajador no le llega (D1).
 */
public class OpenIntakeOriginal {

    private final IntakeRepository intake;
    private final DocumentRepository documents;
    private final OriginalFileStore files;
    private final AuditLog audit;

    public OpenIntakeOriginal(IntakeRepository intake, DocumentRepository documents, OriginalFileStore files,
            AuditLog audit) {
        this.intake = intake;
        this.documents = documents;
        this.files = files;
        this.audit = audit;
    }

    public OriginalFile execute(Actor actor, CompanyId companyId, IntakeBatchId batchId, DocumentId documentId) {
        IntakeBatches.require(intake, actor, companyId, batchId);
        Document d = documents.findEntryIncludingWithdrawn(CompanyScope.of(companyId), ProjectVisibility.all(),
                documentId).map(DocumentEntry::document)
                .filter(x -> x.intakeBatchId().equals(Optional.of(batchId)) && x.intakePending() && !x.withdrawn())
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        OriginalFile original = new OriginalFile(d.filename(), d.format(), files.read(d.sha256()));
        audit.record(AuditSupport.onDocument(AuditEntry.by(actor, AuditAction.DOCUMENT_ORIGINAL_DOWNLOADED), d)
                .with("intakeBatchId", batchId.value().toString()));
        return original;
    }
}
