package com.e2d.odin.application.search;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.search.QuoteMatcher;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;

public class VerifyCitation {

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final DocumentRepository documents;

    public VerifyCitation(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.documents = documents;
    }

    public boolean execute(Actor actor, DocumentId id, int pageNumber, String quote) {
        var content = WorkerAccess.require(actor, organizations, projectMembers, Capability.SEARCH_KNOWLEDGE)
                .content();
        if (documents.findById(actor.scope(), content, id).filter(d -> d.status() != DocumentStatus.READY).isPresent()) {
            throw new NotFoundException("Documento no encontrado");
        }
        return documents.findPage(actor.scope(), content, id, pageNumber)
                .map(page -> QuoteMatcher.containsLiteral(page.text(), quote)).orElse(false);
    }
}
