package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.NotFoundException;
import java.time.Clock;

public class RevokeAccessKey {

    private final AccessKeyRepository keys;
    private final UserRepository users;
    private final Clock clock;

    public RevokeAccessKey(AccessKeyRepository keys, UserRepository users, Clock clock) {
        this.keys = keys;
        this.users = users;
        this.clock = clock;
    }

    public void execute(Actor actor, AccessKeyId id) {
        AccessKey key = keys.findById(id).orElseThrow(() -> new NotFoundException("Clave no encontrada"));
        User owner = users.findById(key.userId()).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        UserAdministration.requireAdminOfUser(actor, owner);
        if (key.revokedAt().isEmpty()) {
            keys.save(key.revoked(clock.instant()));
        }
    }
}
