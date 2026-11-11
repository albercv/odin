package com.e2d.odin.domain.shared;

import java.util.Set;

public record CompanyScope(Set<CompanyId> companies, boolean allCompanies) {

    public CompanyScope {
        companies = Set.copyOf(companies);
    }

    public static CompanyScope of(CompanyId id) {
        return new CompanyScope(Set.of(id), false);
    }

    public static CompanyScope all() {
        return new CompanyScope(Set.of(), true);
    }

    public boolean includes(CompanyId id) {
        return allCompanies || companies.contains(id);
    }
}
