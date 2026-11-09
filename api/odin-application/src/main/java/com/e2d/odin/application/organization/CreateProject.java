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
        Project project = new Project(new ProjectId(ids.newId()), companyId, Validation.requireText(name, "el nombre"));
        organizations.saveProject(project);
        return project;
    }
}
