package com.e2d.odin.application.search;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;

public class ReadPage {

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final DocumentRepository documents;

    public ReadPage(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.documents = documents;
    }

    public PageView execute(Actor actor, DocumentId id, int pageNumber) {
        var visibility = WorkerAccess.require(actor, organizations, projectMembers, Capability.SEARCH_KNOWLEDGE)
                .visibility();
        Document document = documents.findById(actor.scope(), visibility, id).filter(d -> d.status() == DocumentStatus.READY)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        var page = documents.findPage(actor.scope(), visibility, id, pageNumber)
                .orElseThrow(() -> new NotFoundException("Página no encontrada"));
        return new PageView(document, page);
    }
}
