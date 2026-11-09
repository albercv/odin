package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.List;

public class ListProjects {

    private final OrganizationRepository organizations;

    public ListProjects(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    public List<Project> execute(Actor actor, CompanyId companyId) {
        if (!actor.scope().includes(companyId)) {
            throw new ForbiddenException("No tienes permiso sobre esta empresa");
        }
        organizations.findCompany(companyId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        return organizations.listProjects(companyId);
    }
}
