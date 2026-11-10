package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.shared.SessionSettings;
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

class RemoveMemberTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final StoredAdminSessions sessions = new StoredAdminSessions(new InMemoryAdminSessionRepository(),
            new FakeSecretHasher(), new SequenceTokenGenerator(), new SessionSettings(), new MutableClock());
    private final RemoveMember remove = new RemoveMember(memberships, sessions, new MutableClock());
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private final CompanyId other = new CompanyId(UUID.randomUUID());
    private final UserId workerId = new UserId(UUID.randomUUID());
    private final UserId adminId = new UserId(UUID.randomUUID());

    RemoveMemberTest() {
        People.member(users, memberships, workerId, "Wanda", Optional.empty(), Role.WORKER, company, true,
                Optional.empty());
        People.member(users, memberships, adminId, "Carlos", Optional.of("c@demo.test"), Role.COMPANY_ADMIN, company,
                true, Optional.of("pw:x"));
    }

    @Test
    void removesOnlyThatCompanysMembership() {
        memberships.save(new Membership(workerId, other, Set.of(Role.WORKER), true));

        remove.execute(Actors.companyAdmin(company), company, workerId);

        assertThat(memberships.find(workerId, company)).isEmpty();
        assertThat(memberships.find(workerId, other)).isPresent();
    }

    @Test
    void personWithNoMembershipLeftStillExists() {
        remove.execute(Actors.companyAdmin(company), company, workerId);

        assertThat(memberships.listByUser(workerId)).isEmpty();
        assertThat(users.findById(workerId)).isPresent();
    }

    @Test
    void cannotRemoveOneself() {
        Actor self = new Actor(adminId, "Carlos", Role.COMPANY_ADMIN, Set.of(company));

        assertThatThrownBy(() -> remove.execute(self, company, adminId))
                .isInstanceOf(ValidationException.class).hasMessage("No puedes borrarte a ti mismo");
        assertThat(memberships.find(adminId, company)).isPresent();
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        assertThatThrownBy(() -> remove.execute(Actors.companyAdmin(other), company, workerId))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> remove.execute(Actors.worker(company), company, workerId))
                .isInstanceOf(ForbiddenException.class);
        assertThat(memberships.find(workerId, company)).isPresent();
    }

    @Test
    void companyAdminCannotRemoveACompanyAdminMembership() {
        assertThatThrownBy(() -> remove.execute(Actors.companyAdmin(company), company, adminId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Solo el administrador del grupo da o quita la administración de una empresa");
        assertThat(memberships.find(adminId, company)).isPresent();
    }

    @Test
    void groupAdminRemovingTheOnlyAdminMembershipClosesSessions() {
        String token = sessions.open(adminId);

        remove.execute(Actors.groupAdmin(), company, adminId);

        assertThat(memberships.find(adminId, company)).isEmpty();
        assertThat(sessions.resolve(token)).isEmpty();
    }

    @Test
    void removingAMembershipOfAnotherCompanyIsNotFound() {
        assertThatThrownBy(() -> remove.execute(Actors.companyAdmin(other), other, workerId))
                .isInstanceOf(NotFoundException.class);
        assertThat(memberships.find(workerId, company)).isPresent();
    }
}
