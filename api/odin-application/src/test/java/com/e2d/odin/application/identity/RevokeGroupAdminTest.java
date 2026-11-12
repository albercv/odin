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
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevokeGroupAdminTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final StoredAdminSessions sessions = new StoredAdminSessions(new InMemoryAdminSessionRepository(),
            new FakeSecretHasher(), new SequenceTokenGenerator(), new SessionSettings(), new MutableClock());
    private final RevokeGroupAdmin revoke = new RevokeGroupAdmin(users, memberships, sessions);
    private final UserId gemaId = new UserId(UUID.randomUUID());
    private final UserId gaelId = new UserId(UUID.randomUUID());
    private final Actor gema = new Actor(gemaId, "Gema", Role.GROUP_ADMIN, Set.of());

    RevokeGroupAdminTest() {
        People.groupAdmin(users, gemaId, "Gema", Optional.of("g@demo.test"), true, Optional.of("pw:x"));
        People.groupAdmin(users, gaelId, "Gael", Optional.of("gael@demo.test"), true, Optional.of("pw:x"));
    }

    @Test
    void revokesTheMarkAndClosesSessionsIfNoLongerAdministering() {
        String token = sessions.open(gaelId);

        revoke.execute(gema, gaelId);

        assertThat(users.findById(gaelId).orElseThrow().groupAdmin()).isFalse();
        assertThat(users.findById(gaelId).orElseThrow().active()).isTrue();
        assertThat(sessions.resolve(token)).isEmpty();
    }

    @Test
    void keepsSessionsOfSomeoneWhoStillAdministersACompany() {
        CompanyId company = new CompanyId(UUID.randomUUID());
        memberships.save(new Membership(gaelId, company, Set.of(Role.COMPANY_ADMIN), true));
        String token = sessions.open(gaelId);

        revoke.execute(gema, gaelId);

        assertThat(sessions.resolve(token)).isPresent();
    }

    @Test
    void cannotRevokeTheLastActiveGroupAdmin() {
        users.save(users.findById(gemaId).orElseThrow().withActive(false));
        Actor other = new Actor(new UserId(UUID.randomUUID()), "Otra", Role.GROUP_ADMIN, Set.of());

        assertThatThrownBy(() -> revoke.execute(other, gaelId)).isInstanceOf(ConflictException.class)
                .hasMessage("Tiene que quedar al menos un administrador del grupo activo");
        assertThat(users.findById(gaelId).orElseThrow().groupAdmin()).isTrue();
    }

    @Test
    void cannotRevokeOneself() {
        assertThatThrownBy(() -> revoke.execute(gema, gemaId)).isInstanceOf(ValidationException.class)
                .hasMessage("No puedes quitarte tu propia administración del grupo");
        assertThat(users.findById(gemaId).orElseThrow().groupAdmin()).isTrue();
    }

    @Test
    void personWhoIsNotGroupAdminIsNotFound() {
        UserId workerId = new UserId(UUID.randomUUID());
        People.member(users, memberships, workerId, "Wanda", Optional.empty(), Role.WORKER,
                new CompanyId(UUID.randomUUID()), true, Optional.empty());

        assertThatThrownBy(() -> revoke.execute(gema, workerId))
                .isInstanceOf(com.e2d.odin.domain.shared.NotFoundException.class);
    }

    @Test
    void companyAdminIsForbidden() {
        assertThatThrownBy(() -> revoke.execute(Actors.companyAdmin(new CompanyId(UUID.randomUUID())), gaelId))
                .isInstanceOf(ForbiddenException.class);
        assertThat(users.findById(gaelId).orElseThrow().groupAdmin()).isTrue();
    }
}
