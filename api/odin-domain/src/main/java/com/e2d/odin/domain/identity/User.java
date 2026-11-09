package com.e2d.odin.domain.identity;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;

public record User(UserId id, String displayName, Optional<String> email, Role role,
        Optional<CompanyId> companyId, boolean active, Optional<String> passwordHash) {

    public User deactivated() {
        return new User(id, displayName, email, role, companyId, false, passwordHash);
    }
}
