package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;

public class ChangePassword {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final PasswordHasher passwords;
    private final AdminSessions sessions;

    public ChangePassword(UserRepository users, MembershipRepository memberships, PasswordHasher passwords,
            AdminSessions sessions) {
        this.users = users;
        this.memberships = memberships;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    public void execute(Actor actor, UserId target, Optional<String> currentPassword, String newPassword) {
        UserSummary summary = UserAdministration.loadAdministrable(users, memberships, actor, target);
        User user = summary.user();
        if (summary.role() == Role.WORKER) {
            throw new ValidationException(UserAdministration.WORKER_HAS_NO_PASSWORD);
        }
        requireAllowedToSet(actor, user, currentPassword);
        users.save(user.withPasswordHash(passwords.hash(Validation.requirePassword(newPassword))));
        sessions.closeAllOf(target);
    }

    private void requireAllowedToSet(Actor actor, User user, Optional<String> currentPassword) {
        if (actor.userId().equals(user.id())) {
            boolean correct = currentPassword.isPresent() && user.passwordHash()
                    .filter(hash -> passwords.matches(currentPassword.get(), hash)).isPresent();
            if (!correct) {
                throw new ValidationException("La contraseña actual no es correcta");
            }
        } else if (!actor.isGroupAdmin()) {
            throw new ForbiddenException("Solo el administrador del grupo restablece la contraseña de otro usuario");
        }
    }
}
