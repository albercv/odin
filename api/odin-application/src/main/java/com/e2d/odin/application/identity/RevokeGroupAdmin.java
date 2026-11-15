package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;

/** Quita la marca de administrador del grupo; la persona sigue existiendo con sus pertenencias. */
public class RevokeGroupAdmin {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final AdminSessions sessions;
    private final AuditLog audit;

    public RevokeGroupAdmin(UserRepository users, MembershipRepository memberships, AdminSessions sessions,
            AuditLog audit) {
        this.audit = audit;
        this.users = users;
        this.memberships = memberships;
        this.sessions = sessions;
    }

    public void execute(Actor actor, UserId id) {
        AccessPolicy.requireGroupAdmin(actor);
        User user = users.findById(id).filter(User::groupAdmin)
                .orElseThrow(() -> new NotFoundException("Esa persona no es administradora del grupo"));
        if (actor.userId().equals(id)) {
            throw new ValidationException("No puedes quitarte tu propia administración del grupo");
        }
        UserAdministration.requireAnotherActiveGroupAdmin(users, user);
        User revoked = user.withGroupAdmin(false);
        users.save(revoked);
        audit.record(AuditEntry.by(actor, AuditAction.GROUP_ADMIN_REVOKED).on("USER", id.value().toString(), null));
        UserAdministration.closeSessionsIfNoLongerAdministering(revoked, memberships, sessions);
    }
}
