package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;

public class RemoveProjectMember {

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final AuditLog audit;

    public RemoveProjectMember(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            AuditLog audit) {
        this.audit = audit;
        this.organizations = organizations;
        this.projectMembers = projectMembers;
    }

    public void execute(Actor actor, CompanyId companyId, ProjectId projectId, UserId userId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        organizations.findProject(companyId, projectId)
                .orElseThrow(() -> new NotFoundException("Proyecto no encontrado"));
        projectMembers.remove(companyId, projectId, userId);
        audit.record(AuditEntry.by(actor, AuditAction.PROJECT_MEMBER_REMOVED).onCompany(companyId).onProject(projectId)
                .on("USER", userId.value().toString(), null));
    }
}
