package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;

public class DescribeMe {

    private final OrganizationRepository organizations;

    public DescribeMe(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    public MeView execute(Actor actor) {
        CompanyId companyId = actor.requireCompany();
        Company company = organizations.findCompany(companyId)
                .orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        return new MeView(actor.userId(), actor.displayName(), actor.role(), company,
                organizations.listProjects(companyId));
    }
}
