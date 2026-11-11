package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.Optional;

public class CreateProject {

    private final OrganizationRepository organizations;
    private final IdGenerator ids;

    public CreateProject(OrganizationRepository organizations, IdGenerator ids) {
        this.organizations = organizations;
        this.ids = ids;
    }

    public Project execute(Actor actor, CompanyId companyId, String name) {
        AccessPolicy.requireAdminOf(actor, companyId);
        organizations.findCompany(companyId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        String projectName = Validation.requireText(name, "el nombre");
        ProjectNames.requireFree(organizations, companyId, projectName, Optional.empty());
        Project project = new Project(new ProjectId(ids.newId()), companyId, projectName);
        organizations.saveProject(project);
        return project;
    }
}
