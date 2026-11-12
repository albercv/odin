package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;

/** Las claves de una pertenencia: las que esa persona tiene en esa empresa, no las de otras. */
public class ListAccessKeys {

    private final AccessKeyRepository keys;
    private final MembershipRepository memberships;

    public ListAccessKeys(AccessKeyRepository keys, MembershipRepository memberships) {
        this.keys = keys;
        this.memberships = memberships;
    }

    public List<AccessKey> execute(Actor actor, CompanyId companyId, UserId userId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        UserAdministration.requireMembership(memberships, companyId, userId);
        return keys.listByMembership(userId, companyId);
    }
}
