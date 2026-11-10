package com.e2d.odin.domain.identity;

import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;

/** Persona. Su pertenencia a cada empresa vive en {@link Membership}. */
public record User(UserId id, String displayName, Optional<String> email, boolean groupAdmin, boolean active,
        Optional<String> passwordHash) {

    public User updated(String newName, Optional<String> newEmail, boolean newActive) {
        return new User(id, newName, newEmail, groupAdmin, newActive, passwordHash);
    }

    public User withPasswordHash(String newPasswordHash) {
        return new User(id, displayName, email, groupAdmin, active, Optional.of(newPasswordHash));
    }
}
