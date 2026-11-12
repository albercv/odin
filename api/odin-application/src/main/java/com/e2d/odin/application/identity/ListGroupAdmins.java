package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import java.util.List;

public class ListGroupAdmins {

    private final UserRepository users;

    public ListGroupAdmins(UserRepository users) {
        this.users = users;
    }

    public List<User> execute(Actor actor) {
        AccessPolicy.requireGroupAdmin(actor);
        return users.listGroupAdmins();
    }
}
