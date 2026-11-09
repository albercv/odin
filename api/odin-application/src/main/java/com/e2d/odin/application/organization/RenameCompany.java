package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;

public class RenameCompany {

    private final OrganizationRepository organizations;

    public RenameCompany(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    public Company execute(Actor actor, CompanyId id, String name) {
        AccessPolicy.requireGroupAdmin(actor);
        Company renamed = organizations.findCompany(id).orElseThrow(() -> new NotFoundException("Empresa no encontrada"))
                .withName(Validation.requireText(name, "el nombre"));
        organizations.saveCompany(renamed);
        return renamed;
    }
}
