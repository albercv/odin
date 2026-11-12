package com.e2d.odin.application.document;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.util.Optional;

/** Lo decide una persona (D-049): solo este caso de uso pone o quita la marca de sustituido. */
public class SupersedeDocument {

    private final DocumentRepository documents;
    private final Clock clock;

    public SupersedeDocument(DocumentRepository documents, Clock clock) {
        this.documents = documents;
        this.clock = clock;
    }

    public DocumentEntry mark(Actor actor, DocumentId documentId, DocumentId replacementId) {
        Document document = requireDocument(actor, documentId);
        if (replacementId.equals(documentId)) {
            throw new ValidationException("Un documento no puede sustituirse a sí mismo");
        }
        // El sustituto es de la misma empresa que el documento, también para el administrador del grupo.
        CompanyScope company = CompanyScope.of(document.companyId());
        Document replacement = documents.findById(company, ProjectVisibility.all(), replacementId)
                .orElseThrow(() -> new NotFoundException("Documento que lo sustituye no encontrado"));
        requireVisibleToWhoSeesTheDocument(document, replacement);
        rejectCircularChain(company, document, replacement);
        documents.markSuperseded(document.companyId(), documentId, replacementId, clock.instant());
        return documents.findEntry(company, ProjectVisibility.all(), documentId).orElseThrow();
    }

    public void unmark(Actor actor, DocumentId documentId) {
        Document document = requireDocument(actor, documentId);
        documents.clearSuperseded(document.companyId(), documentId);
    }

    private Document requireDocument(Actor actor, DocumentId documentId) {
        AccessPolicy.requireAdmin(actor);
        return documents.findById(actor.scope(), ProjectVisibility.all(), documentId)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
    }

    private static void requireVisibleToWhoSeesTheDocument(Document document, Document replacement) {
        if (replacement.status() != DocumentStatus.READY) {
            throw new ConflictException(
                    "El documento que lo sustituye aún no está listo. Márcalo cuando termine de procesarse.");
        }
        if (replacement.projectId().isPresent() && !replacement.projectId().equals(document.projectId())) {
            throw new ConflictException("El documento que lo sustituye tiene que estar en el mismo proyecto o sin "
                    + "proyecto, para que lo vea todo el que ve este.");
        }
    }

    /** Sigue las marcas desde el sustituto; si llega al documento, la cadena sería circular. */
    private void rejectCircularChain(CompanyScope company, Document document, Document replacement) {
        Optional<Document> current = Optional.of(replacement);
        for (int hops = 0; hops < Document.MAX_SUPERSESSION_CHAIN && current.isPresent(); hops++) {
            Optional<DocumentId> next = current.get().supersededBy();
            if (next.filter(document.id()::equals).isPresent()) {
                throw new ConflictException("Ese documento ya está sustituido, directa o indirectamente, por este.");
            }
            current = next.flatMap(id -> documents.findById(company, ProjectVisibility.all(), id));
        }
    }
}
