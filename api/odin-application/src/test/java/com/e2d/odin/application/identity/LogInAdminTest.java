package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UnauthorizedException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LogInAdminTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryAdminSessionRepository sessions = new InMemoryAdminSessionRepository();
    private final FakeSecretHasher secrets = new FakeSecretHasher();
    private final LogInAdmin logIn = new LogInAdmin(users, sessions, new FakePasswordHasher(), secrets,
            new SequenceTokenGenerator(), new MutableClock());
    private final UserId adminId = new UserId(UUID.randomUUID());

    LogInAdminTest() {
        users.save(new User(adminId, "Gema", Optional.of("g@demo.test"), Role.GROUP_ADMIN, Optional.empty(), true,
                Optional.of("pw:una-clave-larga-1")));
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
}
