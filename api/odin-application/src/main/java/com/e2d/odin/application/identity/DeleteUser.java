package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;

public class DeleteUser {

    private final UserRepository users;
    private final AdminSessions sessions;
    private final Clock clock;

    public DeleteUser(UserRepository users, AdminSessions sessions, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.clock = clock;
    }

    public void execute(Actor actor, UserId id) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        UserAdministration.requireAdminOfUser(actor, user);
        if (actor.userId().equals(id)) {
            throw new ValidationException("No puedes borrarte a ti mismo");
        }
        users.delete(id, clock.instant());
        sessions.closeAllOf(id);
    }
}
