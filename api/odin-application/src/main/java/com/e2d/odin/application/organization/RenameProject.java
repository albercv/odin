package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.Optional;

public class RenameProject {

    private final OrganizationRepository organizations;

    public RenameProject(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    public Project execute(Actor actor, CompanyId companyId, ProjectId id, String name) {
        AccessPolicy.requireAdminOf(actor, companyId);
        Project project = organizations.findProject(companyId, id)
                .orElseThrow(() -> new NotFoundException("Proyecto no encontrado"));
        String newName = Validation.requireText(name, "el nombre");
        ProjectNames.requireFree(organizations, companyId, newName, Optional.of(id));
        Project renamed = project.withName(newName);
        organizations.saveProject(renamed);
        return renamed;
    }
}
