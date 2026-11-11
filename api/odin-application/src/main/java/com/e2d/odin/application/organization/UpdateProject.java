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

/** Cambia el nombre o si el proyecto es visible para toda la empresa; los miembros no se tocan. */
public class UpdateProject {

    private final OrganizationRepository organizations;

    public UpdateProject(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    public Project execute(Actor actor, CompanyId companyId, ProjectId id, ProjectChanges changes) {
        AccessPolicy.requireAdminOf(actor, companyId);
        Project project = organizations.findProject(companyId, id)
                .orElseThrow(() -> new NotFoundException("Proyecto no encontrado"));
        Project updated = project;
        if (changes.name().isPresent()) {
            String newName = Validation.requireText(changes.name().get(), "el nombre");
            ProjectNames.requireFree(organizations, companyId, newName, Optional.of(id));
            updated = updated.withName(newName);
        }
        updated = updated.withRestricted(changes.restricted().orElse(project.restricted()));
        organizations.saveProject(updated);
        return updated;
    }
}
