package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.List;

public class ListUsers {

    private final UserRepository users;

    public ListUsers(UserRepository users) {
        this.users = users;
    }

    public List<User> execute(Actor actor, CompanyId companyId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        return users.listByCompany(companyId);
    }
}
