package com.e2d.odin.domain.organization;

import com.e2d.odin.domain.shared.CompanyId;
import java.util.Set;

public record Company(CompanyId id, String name, Set<Capability> capabilities) {

    public Company {
        capabilities = Set.copyOf(capabilities);
    }

    public Company withName(String newName) {
        return new Company(id, newName, capabilities);
    }

    public Company withCapabilities(Set<Capability> newCapabilities) {
        return new Company(id, name, newCapabilities);
    }
}
