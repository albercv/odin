package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ListMembers {

    private final UserRepository users;
    private final MembershipRepository memberships;

    public ListMembers(UserRepository users, MembershipRepository memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    public List<MemberView> execute(Actor actor, CompanyId companyId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        List<Membership> members = memberships.listByCompany(companyId);
        Map<UserId, User> people = users
                .findAllById(members.stream().map(Membership::userId).toList()).stream()
                .collect(Collectors.toMap(User::id, Function.identity()));
        return members.stream().filter(m -> people.containsKey(m.userId()))
                .map(m -> MemberView.of(people.get(m.userId()), m)).toList();
    }
}
