package com.e2d.odin.application.context;

import com.e2d.odin.application.document.SupersedeDocument;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.DocumentLinkReview;
import com.e2d.odin.application.port.DocumentLinkReview.Link;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.citation.LinkRelation;
import com.e2d.odin.domain.citation.LinkStatus;
import com.e2d.odin.domain.context.DocumentContext;
import com.e2d.odin.domain.context.DocumentVersion;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Un vínculo detectado no marca nada por sí mismo (D-050): lo confirma o lo descarta un administrador. */
public class ReviewDocumentLinks {

    public record AdminContext(List<DocumentVersion> versions, List<DocumentLinkReview.LinkView> links) {
    }

    private final DocumentLinkReview links;
    private final DocumentContexts contexts;
    private final DocumentRepository documents;
    private final SupersedeDocument supersede;
    private final Clock clock;
    private final AuditLog audit;

    public ReviewDocumentLinks(DocumentLinkReview links, DocumentContexts contexts, DocumentRepository documents,
            SupersedeDocument supersede, Clock clock, AuditLog audit) {
        this.links = links;
        this.contexts = contexts;
        this.documents = documents;
        this.supersede = supersede;
        this.clock = clock;
        this.audit = audit;
    }

    public AdminContext context(Actor actor, DocumentId documentId) {
        AccessPolicy.requireAdmin(actor);
        documents.findById(actor.scope(), ProjectVisibility.all(), documentId)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        DocumentContext context = contexts.of(actor.scope(), ProjectVisibility.all(), List.of(documentId))
                .get(documentId);
        return new AdminContext(context == null ? List.of() : context.versions(),
                links.linksOf(actor.scope(), documentId));
    }

    /** REPLACES y RECTIFIES marcan al destino como sustituido por el origen; MODIFIES queda como relación. */
    public void confirm(Actor actor, UUID linkId) {
        Link link = require(actor, linkId);
        if (link.status() == LinkStatus.CONFIRMED) {
            return;
        }
        DocumentId target = link.target().orElseThrow(() -> new ConflictException(
                "El vínculo aún no apunta a ningún documento: no se puede confirmar."));
        if (link.relation() != LinkRelation.MODIFIES) {
            supersede.mark(actor, target, link.source());
        }
        review(actor, link, LinkStatus.CONFIRMED, AuditAction.DOCUMENT_LINK_CONFIRMED);
    }

    public void dismiss(Actor actor, UUID linkId) {
        Link link = require(actor, linkId);
        if (link.status() != LinkStatus.DISMISSED) {
            review(actor, link, LinkStatus.DISMISSED, AuditAction.DOCUMENT_LINK_DISMISSED);
        }
    }

    private Link require(Actor actor, UUID linkId) {
        AccessPolicy.requireAdmin(actor);
        return links.find(actor.scope(), linkId).orElseThrow(() -> new NotFoundException("Vínculo no encontrado"));
    }

    private void review(Actor actor, Link link, LinkStatus status, AuditAction action) {
        links.setStatus(link.companyId(), link.id(), status, actor.userId(), clock.instant());
        AuditEntry entry = AuditEntry.by(actor, action).onCompany(link.companyId())
                .on("DOCUMENT_LINK", link.id().toString(), null).with("relation", link.relation().name())
                .with("sourceId", link.source().value().toString());
        audit.record(link.target().map(t -> entry.with("targetId", t.value().toString())).orElse(entry));
    }
}
