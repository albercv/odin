package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LogOutAdminTest {

    private final InMemoryAdminSessionRepository sessions = new InMemoryAdminSessionRepository();
    private final FakeSecretHasher secrets = new FakeSecretHasher();
    private final LogOutAdmin logOut = new LogOutAdmin(new StoredAdminSessions(sessions, secrets,
            new SequenceTokenGenerator(), new SessionSettings(), new MutableClock()));

    private void givenSession(String token) {
        Instant now = Instant.parse("2026-11-09T10:00:00Z");
        sessions.save(new AdminSession(secrets.hash(token), new UserId(UUID.randomUUID()), now, now));
    }

    @Test
    void logoutDeletesOnlyThatSession() {
        givenSession("uno");
        givenSession("dos");

        logOut.execute("uno");

        assertThat(sessions.findByTokenHash(secrets.hash("uno"))).isEmpty();
        assertThat(sessions.findByTokenHash(secrets.hash("dos"))).isPresent();
    }

    @Test
    void nullTokenChangesNothing() {
        givenSession("uno");

        logOut.execute(null);

        assertThat(sessions.findByTokenHash(secrets.hash("uno"))).isPresent();
    }
}
