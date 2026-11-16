package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Company;
import java.util.Optional;

/** Quién es el administrador que llama con su clave del chat; sin capacidades ni contenido. */
public class DescribeAdminChat {

    public record Me(Actor actor, Optional<Company> company) {
    }

    private final OrganizationRepository organizations;

    public DescribeAdminChat(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    public Me execute(ChatAccess access) {
        return new Me(access.actor(), access.key().companyId().flatMap(organizations::findCompany));
    }
}
