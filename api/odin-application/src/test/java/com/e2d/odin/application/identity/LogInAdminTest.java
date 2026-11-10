package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UnauthorizedException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.UUID;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.User;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LogInAdminTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final InMemoryAdminSessionRepository sessions = new InMemoryAdminSessionRepository();
    private final FakeSecretHasher secrets = new FakeSecretHasher();
    private final LogInAdmin logIn = new LogInAdmin(users, memberships, new FakePasswordHasher(),
            new StoredAdminSessions(sessions, secrets, new SequenceTokenGenerator(), new SessionSettings(),
                    new MutableClock()));
    private final UserId adminId = new UserId(UUID.randomUUID());

    LogInAdminTest() {
        People.groupAdmin(users, adminId, "Gema", Optional.of("g@demo.test"), true,
                Optional.of("pw:una-clave-larga-1"));
    }

    @Test
    void returnsTokenAndStoresOnlyHash() {
        NewSession session = logIn.execute("g@demo.test", "una-clave-larga-1");

        assertThat(session.userId()).isEqualTo(adminId);
        assertThat(sessions.findByTokenHash(session.token())).isEmpty();
        assertThat(sessions.findByTokenHash(secrets.hash(session.token()))).isPresent();
    }

    @Test
    void rejectsWrongPasswordWithGenericMessage() {
        assertThatThrownBy(() -> logIn.execute("g@demo.test", "equivocada-123456"))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Credenciales no válidas");
        assertThatThrownBy(() -> logIn.execute("nadie@demo.test", "una-clave-larga-1"))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Credenciales no válidas");
    }

    @Test
    void inactiveAdminCannotLogIn() {
        People.groupAdmin(users, adminId, "Gema", Optional.of("g@demo.test"), false,
                Optional.of("pw:una-clave-larga-1"));

        assertThatThrownBy(() -> logIn.execute("g@demo.test", "una-clave-larga-1"))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Credenciales no válidas");
    }

    @Test
    void workerOnlyCannotLogIn() {
        People.member(users, memberships, new UserId(UUID.randomUUID()), "Wanda", Optional.of("w@demo.test"),
                Role.WORKER, new CompanyId(UUID.randomUUID()), true, Optional.of("pw:una-clave-larga-1"));

        assertThatThrownBy(() -> logIn.execute("w@demo.test", "una-clave-larga-1"))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Credenciales no válidas");
    }

    @Test
    void personWhoIsAdminAndWorkerLogsIn() {
        UserId id = new UserId(UUID.randomUUID());
        users.save(new User(id, "Carlos", Optional.of("c@demo.test"), false, true,
                Optional.of("pw:una-clave-larga-1")));
        memberships.save(new Membership(id, new CompanyId(UUID.randomUUID()), Set.of(Role.COMPANY_ADMIN, Role.WORKER),
                true));

        assertThat(logIn.execute("c@demo.test", "una-clave-larga-1").userId()).isEqualTo(id);
    }

    @Test
    void adminWhoseMembershipIsInactiveCannotLogIn() {
        UserId id = new UserId(UUID.randomUUID());
        users.save(new User(id, "Carlos", Optional.of("c@demo.test"), false, true,
                Optional.of("pw:una-clave-larga-1")));
        memberships.save(new Membership(id, new CompanyId(UUID.randomUUID()), Set.of(Role.COMPANY_ADMIN), false));

        assertThatThrownBy(() -> logIn.execute("c@demo.test", "una-clave-larga-1"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
