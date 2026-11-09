package com.e2d.odin.domain.identity;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;

public record Actor(UserId userId, String displayName, Role role, Optional<CompanyId> companyId) {

    public boolean isGroupAdmin() {
        return role == Role.GROUP_ADMIN;
    }

    public CompanyId requireCompany() {
        return companyId.orElseThrow(() -> new ForbiddenException("Esta operación requiere una empresa"));
    }

    public CompanyScope scope() {
        return isGroupAdmin() ? CompanyScope.all() : CompanyScope.of(requireCompany());
    }
}
