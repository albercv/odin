package com.e2d.odin.application.search;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.List;
import java.util.Optional;

public class ListDocuments {

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final DocumentRepository documents;

    public ListDocuments(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.documents = documents;
    }

    public List<Document> execute(Actor actor, Optional<ProjectId> projectId, int offset, Optional<Integer> limit) {
        var access = WorkerAccess.require(actor, organizations, projectMembers, Capability.SEARCH_KNOWLEDGE);
        WorkerAccess.requireProject(access.visibility(), projectId);
        return documents.list(actor.scope(), access.visibility(), projectId, Optional.of(DocumentStatus.READY), Math.max(0, offset),
                WorkerAccess.cap(limit, 100, 500));
    }
}
