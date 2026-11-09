package com.e2d.odin.application.identity;

import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;

final class UserAdministration {

    private UserAdministration() {
    }

    static void requireAdminOfUser(Actor actor, User user) {
        user.companyId().ifPresentOrElse(company -> AccessPolicy.requireAdminOf(actor, company),
                () -> AccessPolicy.requireGroupAdmin(actor));
    }
}
