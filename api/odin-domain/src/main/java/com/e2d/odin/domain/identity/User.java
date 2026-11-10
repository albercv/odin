package com.e2d.odin.domain.identity;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;

public record User(UserId id, String displayName, Optional<String> email, Role role,
        Optional<CompanyId> companyId, boolean active, Optional<String> passwordHash) {

    public User updated(String newName, Optional<String> newEmail, boolean newActive) {
        return new User(id, newName, newEmail, role, companyId, newActive, passwordHash);
    }

    public User withPasswordHash(String newPasswordHash) {
        return new User(id, displayName, email, role, companyId, active, Optional.of(newPasswordHash));
    }
}
