package com.e2d.odin.application.document;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.Optional;

/** Recuperar devuelve el documento a las búsquedas y a sus expedientes, con las relaciones que conservaba. */
public class RestoreDocument {

    private final DocumentRepository documents;
    private final AuditLog audit;

    public RestoreDocument(DocumentRepository documents, AuditLog audit) {
        this.documents = documents;
        this.audit = audit;
    }

    public DocumentEntry execute(Actor actor, DocumentId documentId) {
        AccessPolicy.requireAdmin(actor);
        DocumentEntry entry = documents.findEntryIncludingWithdrawn(actor.scope(), ProjectVisibility.all(), documentId)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        AccessPolicy.requireAdminOf(actor, entry.document().companyId());
        if (!entry.document().withdrawn()) {
            return entry;
        }
        documents.markWithdrawal(entry.document().withWithdrawal(Optional.empty(), Optional.empty()));
        audit.record(AuditSupport.onDocument(AuditEntry.by(actor, AuditAction.DOCUMENT_RESTORED), entry.document()));
        return documents.findEntryIncludingWithdrawn(actor.scope(), ProjectVisibility.all(), documentId).orElseThrow();
    }
}
