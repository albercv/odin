package com.e2d.odin.application.search;

import com.e2d.odin.application.context.DocumentContexts;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.context.DocumentCorrection;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.search.QuoteMatcher;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VerifyCitation {

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final DocumentRepository documents;
    private final DocumentContexts contexts;

    public VerifyCitation(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents) {
        this(organizations, projectMembers, documents, DocumentContexts.none());
    }

    public VerifyCitation(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, DocumentContexts contexts) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.documents = documents;
        this.contexts = contexts;
    }

    public boolean execute(Actor actor, DocumentId id, int pageNumber, String quote) {
        var content = WorkerAccess.require(actor, organizations, projectMembers, Capability.SEARCH_KNOWLEDGE)
                .content();
        if (documents.findById(actor.scope(), content, id).filter(d -> d.status() != DocumentStatus.READY).isPresent()) {
            throw new NotFoundException("Documento no encontrado");
        }
        return pageContains(actor, content, id, pageNumber, quote);
    }

    /** Un veredicto por cita: además de si está, si el documento está sustituido o corregido. */
    public List<CitationVerdict> executeAll(Actor actor, List<CitationCheck> checks) {
        var content = WorkerAccess.require(actor, organizations, projectMembers, Capability.SEARCH_KNOWLEDGE)
                .content();
        List<Optional<DocumentEntry>> ready = checks.stream().map(c -> readyEntry(actor, content, c.documentId()))
                .toList();
        Map<DocumentId, List<DocumentCorrection>> corrections = contexts.correctionsOf(actor.scope(), content,
                ready.stream().flatMap(Optional::stream).map(e -> e.document().id()).distinct().toList());
        return java.util.stream.IntStream.range(0, checks.size()).mapToObj(i -> {
            CitationCheck c = checks.get(i);
            if (ready.get(i).isEmpty()) {
                return CitationVerdict.UNVERIFIED;
            }
            DocumentEntry entry = ready.get(i).get();
            return new CitationVerdict(pageContains(actor, content, c.documentId(), c.pageNumber(), c.quote()),
                    entry.document().superseded(), entry.replacement(),
                    corrections.getOrDefault(c.documentId(), List.of()));
        }).toList();
    }

    private Optional<DocumentEntry> readyEntry(Actor actor, ProjectVisibility content, DocumentId id) {
        return documents.findEntry(actor.scope(), content, id).filter(e -> e.document().status() == DocumentStatus.READY);
    }

    private boolean pageContains(Actor actor, ProjectVisibility content, DocumentId id, int pageNumber, String quote) {
        return documents.findPage(actor.scope(), content, id, pageNumber)
                .map(page -> QuoteMatcher.containsLiteral(page.text(), quote)).orElse(false);
    }
}
