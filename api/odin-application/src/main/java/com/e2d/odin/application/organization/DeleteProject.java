package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Clock;

public class DeleteProject {

    private final OrganizationRepository organizations;
    private final Clock clock;

    public DeleteProject(OrganizationRepository organizations, Clock clock) {
        this.organizations = organizations;
        this.clock = clock;
    }

    public void execute(Actor actor, CompanyId companyId, ProjectId id) {
        AccessPolicy.requireAdminOf(actor, companyId);
        organizations.findProject(companyId, id).orElseThrow(() -> new NotFoundException("Proyecto no encontrado"));
        organizations.deleteProject(companyId, id, clock.instant());
    }
}
