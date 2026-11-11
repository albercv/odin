package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;

public class UpdateUser {

    private final UserRepository users;
    private final AdminSessions sessions;

    public UpdateUser(UserRepository users, AdminSessions sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    public User execute(Actor actor, UserId id, UserChanges changes) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        UserAdministration.requireAdminOfUser(actor, user);
        boolean active = changes.active().orElse(user.active());
        if (!active && actor.userId().equals(id)) {
            throw new ValidationException("No puedes desactivarte a ti mismo");
        }
        String name = changes.displayName().map(n -> Validation.requireText(n, "el nombre"))
                .orElse(user.displayName());
        User updated = user.updated(name, changes.email().map(e -> newEmail(user, e)).orElse(user.email()), active);
        users.save(updated);
        if (user.active() && !active) {
            sessions.closeAllOf(id);
        }
        return updated;
    }

    private Optional<String> newEmail(User user, String raw) {
        if (raw.isBlank()) {
            if (user.role() != Role.WORKER) {
                throw new ValidationException("El administrador necesita correo");
            }
            return Optional.empty();
        }
        String email = Validation.normalizeEmail(raw);
        UserAdministration.requireEmailFree(users, email, Optional.of(user.id()));
        return Optional.of(email);
    }
}
