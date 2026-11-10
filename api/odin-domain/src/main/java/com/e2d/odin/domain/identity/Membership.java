package com.e2d.odin.domain.identity;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Set;

/** Pertenencia de una persona a una empresa, con los papeles que tiene en ella. */
public record Membership(UserId userId, CompanyId companyId, Set<Role> roles, boolean active) {

    public Membership {
        roles = Set.copyOf(roles);
        if (roles.isEmpty() || roles.contains(Role.GROUP_ADMIN)) {
            throw new ValidationException("Una pertenencia necesita papel de administrador de empresa o de trabajador");
        }
    }

    public boolean isCompanyAdmin() {
        return roles.contains(Role.COMPANY_ADMIN);
    }

    public boolean isWorker() {
        return roles.contains(Role.WORKER);
    }

    public Membership withActive(boolean newActive) {
        return new Membership(userId, companyId, roles, newActive);
    }
}
