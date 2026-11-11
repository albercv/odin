package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DescribeAdminTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final DescribeAdmin describe = new DescribeAdmin(users);
    private final CompanyId company = Fixtures.company(organizations);

    @Test
    void returnsThePersonCodeOfTheActorOnly() {
        UserId mine = Fixtures.person(users, memberships, "Carlos", Role.COMPANY_ADMIN, company, true);
        CompanyId otherCompany = Fixtures.company(organizations);
        UserId theirs = Fixtures.person(users, memberships, "Olga", Role.COMPANY_ADMIN, otherCompany, true);
        Actor actor = new Actor(mine, "Carlos", Role.COMPANY_ADMIN, Set.of(company));

        assertThat(describe.execute(actor).personCode()).isEqualTo(users.findById(mine).orElseThrow().personCode());
        assertThat(describe.execute(actor).personCode())
                .isNotEqualTo(users.findById(theirs).orElseThrow().personCode());
    }

    @Test
    void workersAreForbidden() {
        assertThatThrownBy(() -> describe.execute(Actors.worker(company))).isInstanceOf(ForbiddenException.class);
    }
}
