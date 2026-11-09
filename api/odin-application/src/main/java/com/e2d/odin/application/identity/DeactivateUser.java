package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;

public class DeactivateUser {

    private final UserRepository users;

    public DeactivateUser(UserRepository users) {
        this.users = users;
    }

    public User execute(Actor actor, UserId id) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        UserAdministration.requireAdminOfUser(actor, user);
        User deactivated = user.deactivated();
        users.save(deactivated);
        return deactivated;
    }
}
