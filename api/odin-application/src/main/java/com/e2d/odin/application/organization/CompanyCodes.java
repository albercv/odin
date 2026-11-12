package com.e2d.odin.application.organization;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;

final class CompanyCodes {

    private CompanyCodes() {
    }

    /** Falla si otra empresa (no {@code owner}) ya usa el código. */
    static void requireFree(OrganizationRepository organizations, String code, CompanyId owner) {
        organizations.findCompanyByCode(code).filter(other -> !other.id().equals(owner)).ifPresent(other -> {
            throw new ConflictException("Ya existe una empresa con ese código");
        });
    }
}
