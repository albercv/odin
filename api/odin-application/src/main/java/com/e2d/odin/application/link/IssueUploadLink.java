package com.e2d.odin.application.link;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;

/** Un enlace para subir al expediente de un caso: lo pide quien tiene CASE_FILES y ve el caso, que ha de tener proyecto. */
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
        var caseDocument = CaseUploadAccess.require(actor, organizations, projectMembers, documents, caseId);
        AuditEntry entry = AuditSupport.onCaseFile(AuditEntry.by(actor, AuditAction.UPLOAD_LINK_ISSUED), caseDocument);
        return issuer.issue(AccessLinkKind.UPLOAD, actor, caseDocument.id(), entry, "/subir/", "");
    }

    /** Un enlace para subir a un proyecto: lo pide quien tiene UPLOAD_DOCUMENTS y ve el proyecto vivo. */
    public IssuedLink forProject(Actor actor, ProjectId projectId) {
        Project project = ProjectUploadAccess.require(actor, organizations, projectMembers, projectId);
        AuditEntry entry = AuditEntry.by(actor, AuditAction.UPLOAD_LINK_ISSUED).onCompany(project.companyId())
                .onProject(project.id()).on("PROJECT", project.id().value().toString(), project.name());
        return issuer.issueForProject(actor, projectId, entry, "/subir/");
    }
}
