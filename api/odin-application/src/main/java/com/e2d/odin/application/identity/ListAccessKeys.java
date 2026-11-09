package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;

public class ListAccessKeys {

    private final AccessKeyRepository keys;
    private final UserRepository users;

    public ListAccessKeys(AccessKeyRepository keys, UserRepository users) {
        this.keys = keys;
        this.users = users;
    }

    public List<AccessKey> execute(Actor actor, UserId userId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        UserAdministration.requireAdminOfUser(actor, user);
        return keys.listByUser(userId);
    }
}
