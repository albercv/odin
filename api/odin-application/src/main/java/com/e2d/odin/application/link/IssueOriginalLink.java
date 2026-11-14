package com.e2d.odin.application.link;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;

/** Un enlace para abrir el original de un documento: lo pide quien tiene SEARCH_KNOWLEDGE y ve el documento. */
public class IssueOriginalLink {

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final DocumentRepository documents;
    private final LinkIssuer issuer;

    public IssueOriginalLink(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, LinkIssuer issuer) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.documents = documents;
        this.issuer = issuer;
    }

    public IssuedLink execute(Actor actor, DocumentId documentId, Optional<Integer> page) {
        if (page.filter(p -> p < 1).isPresent()) {
            throw new ValidationException("La página es 1 o mayor");
        }
        var granted = WorkerAccess.require(actor, organizations, projectMembers, Capability.SEARCH_KNOWLEDGE);
        var document = documents.findById(actor.scope(), granted.content(), documentId)
                .filter(d -> d.withdrawnAt().isEmpty())
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        return issuer.issue(AccessLinkKind.ORIGINAL, actor, document.id(), "/v1/originals/",
                page.map(p -> "#page=" + p).orElse(""));
    }
}
