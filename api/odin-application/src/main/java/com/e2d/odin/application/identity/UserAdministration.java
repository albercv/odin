package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;

final class UserAdministration {

    static final String WORKER_HAS_NO_PASSWORD = "Un trabajador no tiene contraseña: entra con su clave personal";

    private UserAdministration() {
    }

    /** Falla si otro usuario (distinto de {@code editing}, si lo hay) ya usa el correo. */
    static void requireEmailFree(UserRepository users, String email, Optional<UserId> editing) {
        users.findByEmail(email).filter(other -> editing.map(id -> !other.id().equals(id)).orElse(true))
                .ifPresent(other -> {
                    throw new ConflictException("Ya existe un usuario con ese correo");
                });
    }

    static void requireAdminOfUser(Actor actor, User user) {
        user.companyId().ifPresentOrElse(company -> AccessPolicy.requireAdminOf(actor, company),
                () -> AccessPolicy.requireGroupAdmin(actor));
    }
}
