package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ListMembers {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;

    public ListMembers(UserRepository users, MembershipRepository memberships,
            OrganizationRepository organizations) {
        this.users = users;
        this.memberships = memberships;
        this.organizations = organizations;
    }

    public List<MemberView> execute(Actor actor, CompanyId companyId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        String companyCode = organizations.findCompany(companyId)
                .orElseThrow(() -> new NotFoundException("Empresa no encontrada")).code();
        List<Membership> members = memberships.listByCompany(companyId);
        Map<UserId, User> people = users
                .findAllById(members.stream().map(Membership::userId).toList()).stream()
                .collect(Collectors.toMap(User::id, Function.identity()));
        return members.stream().filter(m -> people.containsKey(m.userId()))
                .map(m -> MemberView.of(people.get(m.userId()), m, companyCode)).toList();
    }
}
