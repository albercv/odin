package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeleteUserTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final StoredAdminSessions sessions = new StoredAdminSessions(new InMemoryAdminSessionRepository(),
            new FakeSecretHasher(), new SequenceTokenGenerator(), new SessionSettings(), new MutableClock());
    private final DeleteUser delete = new DeleteUser(users, memberships, sessions, new MutableClock());
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private final UserId workerId = new UserId(UUID.randomUUID());

    DeleteUserTest() {
        People.member(users, memberships, workerId, "Wanda", Optional.empty(), Role.WORKER, company, true,
                Optional.empty());
    }

    @Test
    void deletedPersonLosesAllTheirMemberships() {
        memberships.save(new Membership(workerId, new CompanyId(UUID.randomUUID()), Set.of(Role.WORKER), true));

        delete.execute(Actors.groupAdmin(), workerId);

        assertThat(memberships.listByUser(workerId)).isEmpty();
        assertThat(users.findById(workerId)).isEmpty();
    }

    @Test
    void deletingClosesTheUsersSessions() {
        UserId adminId = new UserId(UUID.randomUUID());
        People.member(users, memberships, adminId, "Carlos", Optional.of("c@demo.test"), Role.COMPANY_ADMIN, company,
                true, Optional.of("pw:x"));
        String token = sessions.open(adminId);

        delete.execute(Actors.groupAdmin(), adminId);

        assertThat(sessions.resolve(token)).isEmpty();
    }

    @Test
    void companyAdminCannotDeleteAPerson() {
        assertThatThrownBy(() -> delete.execute(Actors.companyAdmin(company), workerId))
                .isInstanceOf(ForbiddenException.class);
        assertThat(users.findById(workerId)).isPresent();
        assertThat(memberships.find(workerId, company)).isPresent();
    }

    @Test
    void companyAdminCannotDeleteTheGroupAdmin() {
        UserId groupAdminId = new UserId(UUID.randomUUID());
        People.groupAdmin(users, groupAdminId, "Gema", Optional.of("g@demo.test"), true, Optional.of("pw:x"));

        assertThatThrownBy(() -> delete.execute(Actors.companyAdmin(company), groupAdminId))
                .isInstanceOf(ForbiddenException.class);
        assertThat(users.findById(groupAdminId)).isPresent();
    }

    @Test
    void cannotDeleteOneself() {
        UserId adminId = new UserId(UUID.randomUUID());
        People.member(users, memberships, adminId, "Carlos", Optional.of("c@demo.test"), Role.COMPANY_ADMIN, company,
                true, Optional.of("pw:x"));
        Actor self = new Actor(adminId, "Carlos", Role.GROUP_ADMIN, Set.of());

        assertThatThrownBy(() -> delete.execute(self, adminId))
                .isInstanceOf(ValidationException.class).hasMessage("No puedes borrarte a ti mismo");
        assertThat(users.findById(adminId)).isPresent();
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        Actor stranger = Actors.companyAdmin(new CompanyId(UUID.randomUUID()));

        assertThatThrownBy(() -> delete.execute(stranger, workerId)).isInstanceOf(ForbiddenException.class);
        assertThat(users.findById(workerId)).isPresent();
    }

    @Test
    void workerCannotDelete() {
        assertThatThrownBy(() -> delete.execute(Actors.worker(company), workerId))
                .isInstanceOf(ForbiddenException.class);
        assertThat(users.findById(workerId)).isPresent();
    }

    @Test
    void deletingTwiceIsNotFound() {
        delete.execute(Actors.groupAdmin(), workerId);

        assertThatThrownBy(() -> delete.execute(Actors.groupAdmin(), workerId))
                .isInstanceOf(NotFoundException.class);
    }
}
