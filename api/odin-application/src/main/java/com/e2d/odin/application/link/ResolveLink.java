package com.e2d.odin.application.link;

import com.e2d.odin.application.casefile.CaseFileAccess;
import com.e2d.odin.application.identity.ResolveActor;
import com.e2d.odin.application.port.AccessLinkRepository;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.port.SecretHasher;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.link.AccessLink;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DomainException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.time.Clock;
import java.util.Optional;

/**
 * Cada uso de un enlace vuelve a comprobarlo todo: que existe y es del tipo esperado, que no ha caducado, que la
 * persona sigue activa y ve el caso o el documento, y que viene del equipo al que quedó atado. Cualquier fallo es el
 * mismo 404, para que el token no sirva para averiguar nada.
 */
public class ResolveLink {

    private final AccessLinkRepository links;
    private final ResolveActor actors;
    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final DocumentRepository documents;
    private final SecretHasher secrets;
    private final Clock clock;

    public ResolveLink(AccessLinkRepository links, ResolveActor actors, OrganizationRepository organizations,
            ProjectMemberRepository projectMembers, DocumentRepository documents, SecretHasher secrets, Clock clock) {
        this.links = links;
        this.actors = actors;
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.documents = documents;
        this.secrets = secrets;
        this.clock = clock;
    }

    public ResolvedLink execute(String token, String clientAddress, AccessLinkKind kind) {
        try {
            AccessLink link = find(token, kind);
            Actor actor = actors.worker(link.userId(), link.companyId());
            Document target = visibleTarget(actor, link);
            requireBoundTo(link, clientAddress);
            return new ResolvedLink(link, actor, target);
        } catch (DomainException e) {
            throw notFound();
        }
    }

    private AccessLink find(String token, AccessLinkKind kind) {
        if (token == null || token.isBlank()) {
            throw notFound();
        }
        return links.findByTokenHash(secrets.hash(token))
                .filter(l -> l.kind() == kind && l.expiresAt().isAfter(clock.instant()))
                .orElseThrow(ResolveLink::notFound);
    }

    private Document visibleTarget(Actor actor, AccessLink link) {
        if (link.kind() == AccessLinkKind.UPLOAD) {
            return CaseFileAccess.of(actor, Optional.empty(), organizations, projectMembers)
                    .caseDocument(documents, link.caseId().orElseThrow());
        }
        var granted = WorkerAccess.require(actor, organizations, projectMembers, Capability.SEARCH_KNOWLEDGE);
        return documents.findById(CompanyScope.of(link.companyId()), granted.content(), link.documentId().orElseThrow())
                .filter(d -> d.withdrawnAt().isEmpty()).orElseThrow(ResolveLink::notFound);
    }

    private void requireBoundTo(AccessLink link, String clientAddress) {
        String bound = link.boundAddress().orElseGet(() -> links.bind(link, clientAddress));
        if (!bound.equals(clientAddress)) {
            throw notFound();
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Enlace no encontrado");
    }
}
