package com.e2d.odin.domain.identity;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Set;

/** Quién actúa en una petición y con qué papel. {@code companies} está vacío para el administrador del grupo. */
public record Actor(UserId userId, String displayName, Role role, Set<CompanyId> companies) {

    public Actor {
        companies = Set.copyOf(companies);
    }

    public boolean isGroupAdmin() {
        return role == Role.GROUP_ADMIN;
    }

    public boolean administers(CompanyId company) {
        return isGroupAdmin() || (role == Role.COMPANY_ADMIN && companies.contains(company));
    }

    /** La única empresa en la que actúa; falla si no tiene ninguna o tiene varias. */
    public CompanyId requireCompany() {
        if (companies.size() != 1) {
            throw new ForbiddenException("Esta operación requiere una empresa");
        }
        return companies.iterator().next();
    }

    public CompanyScope scope() {
        return isGroupAdmin() ? CompanyScope.all() : new CompanyScope(companies, false);
    }
}
