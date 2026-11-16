package com.e2d.odin.application.document;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;

public class RetryDocument {

    private final DocumentRepository documents;
    private final AuditLog audit;

    public RetryDocument(DocumentRepository documents, AuditLog audit) {
        this.documents = documents;
        this.audit = audit;
    }

    public DocumentEntry execute(Actor actor, DocumentId id) {
        AccessPolicy.requireAdmin(actor);
        Document document = documents.findById(actor.scope(), ProjectVisibility.all(), id)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        if (document.status() != DocumentStatus.FAILED && document.status() != DocumentStatus.PENDING_READER) {
            throw new ConflictException("Solo se reintentan documentos fallidos o pendientes de lector");
        }
        Document requeued = document.requeue().resetAttempts();
        documents.save(requeued);
        audit.record(AuditSupport.onDocument(AuditEntry.by(actor, AuditAction.DOCUMENT_RETRIED), document));
        return documents.findEntry(actor.scope(), ProjectVisibility.all(), id).orElseThrow();
    }
}
