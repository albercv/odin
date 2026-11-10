package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.time.Instant;

/** Borra a la persona entera, con todas sus pertenencias. Solo el administrador del grupo. */
public class DeleteUser {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final AdminSessions sessions;
    private final Clock clock;

    public DeleteUser(UserRepository users, MembershipRepository memberships, AdminSessions sessions, Clock clock) {
        this.users = users;
        this.memberships = memberships;
        this.sessions = sessions;
        this.clock = clock;
    }

    public void execute(Actor actor, UserId id) {
        AccessPolicy.requireGroupAdmin(actor);
        users.findById(id).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        if (actor.userId().equals(id)) {
            throw new ValidationException("No puedes borrarte a ti mismo");
        }
        Instant now = clock.instant();
        memberships.listByUser(id).forEach(m -> memberships.delete(id, m.companyId(), now));
        users.delete(id, now);
        sessions.closeAllOf(id);
    }
}
