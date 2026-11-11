package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.NotFoundException;

/** La persona que administra, tal como la ve ella misma en el panel. */
public class DescribeAdmin {

    private final UserRepository users;

    public DescribeAdmin(UserRepository users) {
        this.users = users;
    }

    public User execute(Actor actor) {
        AccessPolicy.requireAdmin(actor);
        return users.findById(actor.userId()).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }
}
