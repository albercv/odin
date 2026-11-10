package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;

public class ListAccessKeys {

    private final AccessKeyRepository keys;
    private final UserRepository users;
    private final MembershipRepository memberships;

    public ListAccessKeys(AccessKeyRepository keys, UserRepository users, MembershipRepository memberships) {
        this.keys = keys;
        this.users = users;
        this.memberships = memberships;
    }

    public List<AccessKey> execute(Actor actor, UserId userId) {
        UserAdministration.loadAdministrable(users, memberships, actor, userId);
        return keys.listByUser(userId);
    }
}
