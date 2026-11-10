package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListUsersTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final ListUsers list = new ListUsers(users, memberships);
    private final CompanyId company = new CompanyId(UUID.randomUUID());

    ListUsersTest() {
        People.member(users, memberships, new UserId(UUID.randomUUID()), "Wanda", Optional.empty(), Role.WORKER,
                company, true, Optional.empty());
    }

    @Test
    void otherCompanyAdminCannotListUsers() {
        CompanyId other = new CompanyId(UUID.randomUUID());

        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(other), company))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void workerCannotListUsers() {
        assertThatThrownBy(() -> list.execute(Actors.worker(company), company))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ownCompanyAdminListsUsers() {
        assertThat(list.execute(Actors.companyAdmin(company), company)).hasSize(1);
    }
}
