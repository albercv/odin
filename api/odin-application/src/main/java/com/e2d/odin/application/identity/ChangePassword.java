package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;

public class ChangePassword {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final PasswordHasher passwords;
    private final AdminSessions sessions;
    private final AuditLog audit;

    public ChangePassword(UserRepository users, MembershipRepository memberships, PasswordHasher passwords,
            AdminSessions sessions, AuditLog audit) {
        this.audit = audit;
        this.users = users;
        this.memberships = memberships;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    public void execute(Actor actor, UserId target, Optional<String> currentPassword, String newPassword) {
        boolean ownPassword = actor.userId().equals(target);
        if (!ownPassword && !actor.isGroupAdmin()) {
            throw new ForbiddenException("Solo el administrador del grupo restablece la contraseña de otro usuario");
        }
        User user = users.findById(target).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        if (!ownPassword && user.groupAdmin()) {
            throw new ForbiddenException("No puedes cambiar la contraseña de otro administrador del grupo");
        }
        if (!user.groupAdmin() && UserAdministration.companiesAdministeredBy(memberships, target).isEmpty()) {
            throw new ValidationException(UserAdministration.WORKER_HAS_NO_PASSWORD);
        }
        if (ownPassword) {
            requireCurrentPassword(user, currentPassword);
        }
        users.save(user.withPasswordHash(passwords.hash(Validation.requirePassword(newPassword))));
        audit.record(AuditEntry.by(actor, AuditAction.PASSWORD_CHANGED).on("USER", target.value().toString(), null)
                .with("own", ownPassword));
        sessions.closeAllOf(target);
    }

    private void requireCurrentPassword(User user, Optional<String> currentPassword) {
        boolean correct = currentPassword.isPresent() && user.passwordHash()
                .filter(hash -> passwords.matches(currentPassword.get(), hash)).isPresent();
        if (!correct) {
            throw new ValidationException("La contraseña actual no es correcta");
        }
    }
}
