package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListMembersTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository() {
        @Override
        public Optional<User> findById(UserId id) {
            throw new AssertionError("Listar no debe leer una persona por pertenencia");
        }
    };
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final ListMembers list = new ListMembers(users, memberships);
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private final CompanyId other = new CompanyId(UUID.randomUUID());
    private final UserId workerId = new UserId(UUID.randomUUID());
    private final UserId adminId = new UserId(UUID.randomUUID());

    ListMembersTest() {
        People.member(users, memberships, workerId, "Wanda", Optional.empty(), Role.WORKER, company, true,
                Optional.empty());
        People.member(users, memberships, adminId, "Carlos", Optional.of("c@demo.test"), Role.COMPANY_ADMIN, company,
                true, Optional.of("pw:x"));
        memberships.save(new Membership(adminId, other, Set.of(Role.WORKER), true));
        People.member(users, memberships, new UserId(UUID.randomUUID()), "Olga", Optional.empty(), Role.WORKER,
                other, true, Optional.empty());
    }

    @Test
    void listsOnlyThatCompanysMemberships() {
        var members = list.execute(Actors.companyAdmin(company), company);

        assertThat(members).extracting(MemberView::displayName).containsExactlyInAnyOrder("Wanda", "Carlos");
        assertThat(members).filteredOn(m -> m.userId().equals(adminId)).singleElement()
                .satisfies(m -> assertThat(m.roles()).containsExactly(Role.COMPANY_ADMIN));
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(other), company))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> list.execute(Actors.worker(company), company))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void inactiveMembershipIsShownAsInactive() {
        memberships.save(new Membership(workerId, company, Set.of(Role.WORKER), false));

        assertThat(list.execute(Actors.groupAdmin(), company)).filteredOn(m -> m.userId().equals(workerId))
                .singleElement().satisfies(m -> assertThat(m.active()).isFalse());
    }
}
