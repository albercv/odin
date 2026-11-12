package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.EnumSet;

public class CreateCompany {

    private final OrganizationRepository organizations;
    private final IdGenerator ids;

    public CreateCompany(OrganizationRepository organizations, IdGenerator ids) {
        this.organizations = organizations;
        this.ids = ids;
    }

    public Company execute(Actor actor, String name, String code) {
        AccessPolicy.requireGroupAdmin(actor);
        Company company = new Company(new CompanyId(ids.newId()), Validation.requireText(name, "el nombre"),
                code, EnumSet.allOf(Capability.class));
        CompanyCodes.requireFree(organizations, company.code(), company.id());
        organizations.saveCompany(company);
        return company;
    }
}
