package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UnauthorizedException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChangePasswordTest {

    private static final String OLD = "clave-antigua-123";
    private static final String NEW = "clave-nueva-1234";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final FakePasswordHasher passwords = new FakePasswordHasher();
    private final StoredAdminSessions sessions = new StoredAdminSessions(new InMemoryAdminSessionRepository(),
            new FakeSecretHasher(), new SequenceTokenGenerator(), new SessionSettings(), new MutableClock());
    private final ChangePassword change = new ChangePassword(users, passwords, sessions);
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private final UserId adminId = new UserId(UUID.randomUUID());
    private final UserId workerId = new UserId(UUID.randomUUID());
    private final Actor self = new Actor(adminId, "Carlos", Role.COMPANY_ADMIN, Optional.of(company));

    ChangePasswordTest() {
        users.save(new User(adminId, "Carlos", Optional.of("c@demo.test"), Role.COMPANY_ADMIN, Optional.of(company),
                true, Optional.of(passwords.hash(OLD))));
        users.save(new User(workerId, "Wanda", Optional.empty(), Role.WORKER, Optional.of(company), true,
                Optional.empty()));
    }

    private Optional<String> storedHash() {
        return users.findById(adminId).orElseThrow().passwordHash();
    }

    @Test
    void adminChangesOwnPasswordWithCurrentOne() {
        change.execute(self, adminId, Optional.of(OLD), NEW);

        assertThat(storedHash()).contains(passwords.hash(NEW));
    }

    @Test
    void wrongCurrentPasswordIsRejected() {
        assertThatThrownBy(() -> change.execute(self, adminId, Optional.of("otra-clave-12345"), NEW))
                .isInstanceOf(ValidationException.class).hasMessage("La contraseña actual no es correcta");
        assertThat(storedHash()).contains(passwords.hash(OLD));
    }

    @Test
    void missingCurrentPasswordIsRejected() {
        assertThatThrownBy(() -> change.execute(self, adminId, Optional.empty(), NEW))
                .isInstanceOf(ValidationException.class).hasMessage("La contraseña actual no es correcta");
    }

    @Test
    void groupAdminResetsCompanyAdminWithoutCurrentOne() {
        change.execute(Actors.groupAdmin(), adminId, Optional.empty(), NEW);

        assertThat(storedHash()).contains(passwords.hash(NEW));
    }

    @Test
    void companyAdminCannotResetAnotherUser() {
        UserId otherId = new UserId(UUID.randomUUID());
        users.save(new User(otherId, "Olga", Optional.of("o@demo.test"), Role.COMPANY_ADMIN, Optional.of(company),
                true, Optional.of(passwords.hash(OLD))));

        assertThatThrownBy(() -> change.execute(self, otherId, Optional.empty(), NEW))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Solo el administrador del grupo restablece la contraseña de otro usuario");
        assertThat(users.findById(otherId).orElseThrow().passwordHash()).contains(passwords.hash(OLD));
    }

    @Test
    void companyAdminOfAnotherCompanyIsForbidden() {
        Actor stranger = Actors.companyAdmin(new CompanyId(UUID.randomUUID()));

        assertThatThrownBy(() -> change.execute(stranger, adminId, Optional.empty(), NEW))
                .isInstanceOf(ForbiddenException.class);
        assertThat(storedHash()).contains(passwords.hash(OLD));
    }

    @Test
    void workerHasNoPassword() {
        assertThatThrownBy(() -> change.execute(Actors.groupAdmin(), workerId, Optional.empty(), NEW))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Un trabajador no tiene contraseña: entra con su clave personal");
    }

    @Test
    void shortPasswordIsRejected() {
        assertThatThrownBy(() -> change.execute(self, adminId, Optional.of(OLD), "corta"))
                .isInstanceOf(ValidationException.class);
        assertThat(storedHash()).contains(passwords.hash(OLD));
    }

    @Test
    void changingPasswordClosesAllSessionsOfThatUser() {
        String first = sessions.open(adminId);
        String second = sessions.open(adminId);

        change.execute(self, adminId, Optional.of(OLD), NEW);

        assertThat(sessions.resolve(first)).isEmpty();
        assertThat(sessions.resolve(second)).isEmpty();
    }

    @Test
    void oldPasswordNoLongerLogsIn() {
        LogInAdmin logIn = new LogInAdmin(users, passwords, sessions);

        change.execute(self, adminId, Optional.of(OLD), NEW);

        assertThatThrownBy(() -> logIn.execute("c@demo.test", OLD)).isInstanceOf(UnauthorizedException.class);
        assertThat(logIn.execute("c@demo.test", NEW).userId()).isEqualTo(adminId);
    }

    @Test
    void groupAdminCannotResetUserOfUnknownId() {
        assertThatThrownBy(() -> change.execute(Actors.groupAdmin(), new UserId(UUID.randomUUID()), Optional.empty(),
                NEW)).isInstanceOf(NotFoundException.class);
    }
}
