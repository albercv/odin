package com.e2d.odin.domain.identity;

import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;

/**
 * Persona. Su pertenencia a cada empresa vive en {@link Membership}. El código de persona se genera al crearla y no
 * cambia nunca; los apellidos solo faltan en las personas migradas de la fase 1.1.
 */
public record User(UserId id, String firstName, Optional<String> lastName, String personCode,
        Optional<String> email, boolean groupAdmin, boolean active, Optional<String> passwordHash) {

    public String displayName() {
        return lastName.map(last -> firstName + " " + last).orElse(firstName);
    }

    public User withNames(String newFirstName, Optional<String> newLastName) {
        return new User(id, newFirstName, newLastName, personCode, email, groupAdmin, active, passwordHash);
    }

    public User withEmail(Optional<String> newEmail) {
        return new User(id, firstName, lastName, personCode, newEmail, groupAdmin, active, passwordHash);
    }

    public User withActive(boolean newActive) {
        return new User(id, firstName, lastName, personCode, email, groupAdmin, newActive, passwordHash);
    }

    public User withPasswordHash(String newPasswordHash) {
        return new User(id, firstName, lastName, personCode, email, groupAdmin, active,
                Optional.of(newPasswordHash));
    }
}
