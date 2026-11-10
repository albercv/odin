package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.List;
import java.util.Optional;

public class ListUsers {

    private final UserRepository users;
    private final MembershipRepository memberships;

    public ListUsers(UserRepository users, MembershipRepository memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    public List<UserSummary> execute(Actor actor, CompanyId companyId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        return memberships.listByCompany(companyId).stream().map(this::summarize).flatMap(Optional::stream)
                .toList();
    }

    private Optional<UserSummary> summarize(Membership membership) {
        return users.findById(membership.userId())
                .map(user -> new UserSummary(user, Optional.of(membership)));
    }
}
