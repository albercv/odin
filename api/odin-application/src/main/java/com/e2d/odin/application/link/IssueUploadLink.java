package com.e2d.odin.application.link;

import com.e2d.odin.application.casefile.CaseFileAccess;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.shared.DocumentId;
import java.util.Optional;

/** Un enlace para subir ficheros al expediente de un caso: lo pide quien tiene CASE_FILES y ve el caso. */
public class IssueUploadLink {

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final DocumentRepository documents;
    private final LinkIssuer issuer;

    public IssueUploadLink(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, LinkIssuer issuer) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.documents = documents;
        this.issuer = issuer;
    }

    public IssuedLink execute(Actor actor, DocumentId caseId) {
        var caseDocument = CaseFileAccess.of(actor, Optional.empty(), organizations, projectMembers)
                .caseDocument(documents, caseId);
        return issuer.issue(AccessLinkKind.UPLOAD, actor, caseDocument.id(), "/subir/", "");
    }
}
