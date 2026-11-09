package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.Set;

public class SetCompanyCapabilities {

    private final OrganizationRepository organizations;

    public SetCompanyCapabilities(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    public Company execute(Actor actor, CompanyId id, Set<Capability> capabilities) {
        AccessPolicy.requireGroupAdmin(actor);
        Company updated = organizations.findCompany(id).orElseThrow(() -> new NotFoundException("Empresa no encontrada"))
                .withCapabilities(capabilities);
        organizations.saveCompany(updated);
        return updated;
    }
}
