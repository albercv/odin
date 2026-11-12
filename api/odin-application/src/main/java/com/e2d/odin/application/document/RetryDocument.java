package com.e2d.odin.application.document;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;

public class RetryDocument {

    private final DocumentRepository documents;

    public RetryDocument(DocumentRepository documents) {
        this.documents = documents;
    }

    public Document execute(Actor actor, DocumentId id) {
        AccessPolicy.requireAdmin(actor);
        Document document = documents.findById(actor.scope(), ProjectVisibility.all(), id)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        if (document.status() != DocumentStatus.FAILED && document.status() != DocumentStatus.PENDING_READER) {
            throw new ConflictException("Solo se reintentan documentos fallidos o pendientes de lector");
        }
        Document requeued = document.requeue().resetAttempts();
        documents.save(requeued);
        return requeued;
    }
}
