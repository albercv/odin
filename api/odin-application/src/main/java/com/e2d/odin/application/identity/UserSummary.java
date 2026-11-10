package com.e2d.odin.application.identity;

import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.Optional;

/**
 * Persona con su pertenencia, tal como la ve hoy la administración: un solo papel y una sola empresa.
 * El administrador del grupo no tiene pertenencia.
 */
public record UserSummary(User user, Optional<Membership> membership) {

    public Role role() {
        if (user.groupAdmin()) {
            return Role.GROUP_ADMIN;
        }
        return membership.filter(Membership::isCompanyAdmin).isPresent() ? Role.COMPANY_ADMIN : Role.WORKER;
    }

    public Optional<CompanyId> companyId() {
        return membership.map(Membership::companyId);
    }

    public boolean active() {
        return user.active() && membership.map(Membership::active).orElse(true);
    }
}
