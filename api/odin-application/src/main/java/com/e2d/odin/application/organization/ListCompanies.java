package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Company;
import java.util.List;

public class ListCompanies {

    private final OrganizationRepository organizations;

    public ListCompanies(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    public List<Company> execute(Actor actor) {
        return organizations.listCompanies(actor.scope());
    }
}
