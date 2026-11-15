package com.e2d.odin.application.document;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.topic.TaggedDocument;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.List;
import java.util.Optional;

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

    /** La ficha con sus copias escaneadas y de quién es copia. */
    public TaggedDocument detail(Actor actor, DocumentId id) {
        DocumentEntry entry = execute(actor, id);
        ScanLinks links = ScanLinks.of(documents, actor.scope(), ProjectVisibility.all(), Optional.empty(),
                List.of(entry.document())).get(id);
        return new TaggedDocument(entry, Optional.empty(), List.of(), links);
    }
}
