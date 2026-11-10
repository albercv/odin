package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;

public class UpdateUser {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final AdminSessions sessions;

    public UpdateUser(UserRepository users, MembershipRepository memberships, AdminSessions sessions) {
        this.users = users;
        this.memberships = memberships;
        this.sessions = sessions;
    }

    public UserSummary execute(Actor actor, UserId id, UserChanges changes) {
        UserSummary target = UserAdministration.loadAdministrable(users, memberships, actor, id);
        User user = target.user();
        boolean active = changes.active().orElse(target.active());
        if (!active && actor.userId().equals(id)) {
            throw new ValidationException("No puedes desactivarte a ti mismo");
        }
        String name = changes.displayName().map(n -> Validation.requireText(n, "el nombre"))
                .orElse(user.displayName());
        Optional<String> email = changes.email().map(e -> newEmail(target, e)).orElse(user.email());
        UserSummary updated = save(target, name, email, active);
        if (target.active() && !active) {
            sessions.closeAllOf(id);
        }
        return updated;
    }

    /** Activar o desactivar afecta a la pertenencia; solo en el administrador del grupo, sin ella, a la persona. */
    private UserSummary save(UserSummary target, String name, Optional<String> email, boolean active) {
        User user = target.user();
        User updated = user.updated(name, email, user.groupAdmin() ? active : user.active());
        users.save(updated);
        Optional<Membership> membership = target.membership().map(m -> m.withActive(active));
        membership.ifPresent(memberships::save);
        return new UserSummary(updated, membership);
    }

    private Optional<String> newEmail(UserSummary target, String raw) {
        if (raw.isBlank()) {
            if (target.role() != Role.WORKER) {
                throw new ValidationException("El administrador necesita correo");
            }
            return Optional.empty();
        }
        String email = Validation.normalizeEmail(raw);
        UserAdministration.requireEmailFree(users, email, Optional.of(target.user().id()));
        return Optional.of(email);
    }
}
