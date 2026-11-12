package com.e2d.odin.application.document;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;

public class GetDocument {

    private final DocumentRepository documents;

    public GetDocument(DocumentRepository documents) {
        this.documents = documents;
    }

    public DocumentEntry execute(Actor actor, DocumentId id) {
        AccessPolicy.requireAdmin(actor);
        return documents.findEntry(actor.scope(), ProjectVisibility.all(), id)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
    }
}
