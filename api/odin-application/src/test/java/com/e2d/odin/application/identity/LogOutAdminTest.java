package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.port.AuditContext;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditChannel;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LogOutAdminTest {

    private final InMemoryAdminSessionRepository sessions = new InMemoryAdminSessionRepository();
    private final FakeSecretHasher secrets = new FakeSecretHasher();
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final LogOutAdmin logOut = new LogOutAdmin(new StoredAdminSessions(sessions, secrets,
            new SequenceTokenGenerator(), new SessionSettings(), new MutableClock()), audit, () -> AuditContext.SYSTEM);

    private void givenSession(String token) {
        Instant now = Instant.parse("2026-11-09T10:00:00Z");
        sessions.save(new AdminSession(secrets.hash(token), new UserId(UUID.randomUUID()), now, now));
    }

    @Test
    void logoutDeletesOnlyThatSession() {
        givenSession("uno");
        givenSession("dos");

        logOut.execute(Actors.groupAdmin(), "uno");

        assertThat(sessions.findByTokenHash(secrets.hash("uno"))).isEmpty();
        assertThat(sessions.findByTokenHash(secrets.hash("dos"))).isPresent();
    }

    @Test
    void logoutLeavesOneAccessEntryWithClientData() {
        givenSession("uno");
        Actor admin = Actors.groupAdmin();
        LogOutAdmin withClient = new LogOutAdmin(new StoredAdminSessions(sessions, secrets,
                new SequenceTokenGenerator(), new SessionSettings(), new MutableClock()), audit, () ->
                new AuditContext.AuditRequest(AuditChannel.PANEL, Optional.empty(), Optional.of("10.0.0.7"),
                        Optional.of("Firefox")));

        withClient.execute(admin, "uno");

        assertThat(audit.entries).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo(AuditAction.LOGOUT);
            assertThat(e.actorUserId()).contains(admin.userId());
            assertThat(e.actorRole()).contains("GROUP_ADMIN");
            assertThat(e.details()).containsEntry("ip", "10.0.0.7").containsEntry("userAgent", "Firefox");
        });
    }

    @Test
    void nullTokenChangesNothing() {
        givenSession("uno");

        logOut.execute(Actors.groupAdmin(), null);

        assertThat(sessions.findByTokenHash(secrets.hash("uno"))).isPresent();
    }
}
