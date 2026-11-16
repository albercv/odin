package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Clock;

public class DeleteProject {

    private final OrganizationRepository organizations;
    private final Clock clock;
    private final AuditLog audit;

    public DeleteProject(OrganizationRepository organizations, Clock clock, AuditLog audit) {
        this.organizations = organizations;
        this.clock = clock;
        this.audit = audit;
    }

    public void execute(Actor actor, CompanyId companyId, ProjectId id) {
        AccessPolicy.requireAdminOf(actor, companyId);
        Project project = organizations.findProject(companyId, id)
                .orElseThrow(() -> new NotFoundException("Proyecto no encontrado"));
        organizations.deleteProject(companyId, id, clock.instant());
        audit.record(AuditEntry.by(actor, AuditAction.PROJECT_DELETED).onCompany(companyId).onProject(id)
                .on("PROJECT", id.value().toString(), project.name()));
    }
}
