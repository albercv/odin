package com.e2d.odin.application.document;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;

/** Deshace el enlace de «copia escaneada» (D3): el escaneo vuelve a salir como un documento más. */
public class UnlinkScanCopy {

    private final DocumentRepository documents;

    private final AuditLog audit;

    public UnlinkScanCopy(DocumentRepository documents, AuditLog audit) {
        this.documents = documents;
        this.audit = audit;
    }

    public void execute(Actor actor, DocumentId documentId) {
        AccessPolicy.requireAdmin(actor);
        Document scan = documents.findById(actor.scope(), ProjectVisibility.all(), documentId)
                .filter(d -> d.scanOf().isPresent())
                .orElseThrow(() -> new NotFoundException("El documento no es una copia escaneada"));
        documents.clearScanOf(scan.companyId(), documentId);
        audit.record(AuditSupport.onDocument(AuditEntry.by(actor, AuditAction.DOCUMENT_SCAN_UNLINKED), scan)
                .with("mainDocumentId", scan.scanOf().orElseThrow().value().toString()));
    }
}
