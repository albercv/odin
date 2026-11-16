package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.application.port.AuditContext.AuditRequest;
import com.e2d.odin.domain.audit.AuditChannel;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.identity.SessionExpiredException;
import com.e2d.odin.domain.identity.SessionExpiredException.Reason;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoredAdminSessionsTest {

    private final InMemoryAdminSessionRepository repository = new InMemoryAdminSessionRepository();
    private final FakeSecretHasher secrets = new FakeSecretHasher();
    private final MutableClock clock = new MutableClock();
    private final StoredAdminSessions sessions = new StoredAdminSessions(repository, secrets,
            new SequenceTokenGenerator(), new SessionSettings(), clock);
    private final UserId userId = new UserId(UUID.randomUUID());

    @Test
    void openedTokenResolvesToItsUser() {
        String token = sessions.open(userId);

        assertThat(sessions.resolve(token)).contains(userId);
        assertThat(repository.findByTokenHash(token)).isEmpty();
        assertThat(repository.findByTokenHash(secrets.hash(token))).isPresent();
    }

    @Test
    void unknownOrNullTokenResolvesToEmpty() {
        assertThat(sessions.resolve("nada")).isEmpty();
        assertThat(sessions.resolve(null)).isEmpty();
    }

    @Test
    void idleSessionExpiresAndIsDeleted() {
        String token = sessions.open(userId);
        clock.advance(Duration.ofMinutes(30).plusSeconds(1));

        assertThatThrownBy(() -> sessions.resolve(token)).isInstanceOfSatisfying(SessionExpiredException.class,
                e -> {
                    assertThat(e.reason()).isEqualTo(Reason.IDLE);
                    assertThat(e.userId()).isEqualTo(userId);
                });
        assertThat(repository.findByTokenHash(secrets.hash(token))).isEmpty();
    }

    @Test
    void sessionInUseConstantlyStillExpiresAtTheAbsoluteLimit() {
        String token = sessions.open(userId);
        for (int i = 0; i < 24; i++) {
            clock.advance(Duration.ofMinutes(29));
            assertThat(sessions.resolve(token)).contains(userId);
        }
        clock.advance(Duration.ofMinutes(29));

        assertThatThrownBy(() -> sessions.resolve(token)).isInstanceOfSatisfying(SessionExpiredException.class,
                e -> assertThat(e.reason()).isEqualTo(Reason.ABSOLUTE));
        assertThat(repository.findByTokenHash(secrets.hash(token))).isEmpty();
    }

    @Test
    void expiredSessionIsNotSlid() {
        String token = sessions.open(userId);
        AdminSession before = repository.findByTokenHash(secrets.hash(token)).orElseThrow();
        clock.advance(Duration.ofMinutes(31));

        assertThatThrownBy(() -> sessions.resolve(token)).isInstanceOf(SessionExpiredException.class);
        assertThat(sessions.resolve(token)).isEmpty();
        assertThat(before.lastSeenAt()).isNotEqualTo(clock.instant());
    }

    @Test
    void openStoresClientAndTruncatesTheBrowser() {
        String token = sessions.open(userId, new AuditRequest(AuditChannel.PANEL, Optional.empty(),
                Optional.of("10.0.0.1"), Optional.of("x".repeat(400))));

        AdminSession stored = repository.findByTokenHash(secrets.hash(token)).orElseThrow();
        assertThat(stored.ip()).contains("10.0.0.1");
        assertThat(stored.userAgent().orElseThrow()).hasSize(300);
        assertThat(stored.reauthenticatedAt()).isEqualTo(stored.createdAt());
    }

    @Test
    void expiresAtIsTheCloserOfIdleAndAbsolute() {
        String token = sessions.open(userId);
        SessionSettings settings = new SessionSettings();
        Instant created = clock.instant();

        assertThat(settings.expiresAt(sessions.find(token).orElseThrow())).isEqualTo(created.plus(Duration.ofMinutes(30)));
        for (int i = 0; i < 24; i++) {
            clock.advance(Duration.ofMinutes(29));
            sessions.resolve(token);
        }
        assertThat(settings.expiresAt(sessions.find(token).orElseThrow())).isEqualTo(created.plus(Duration.ofHours(12)));
    }

    @Test
    void closeOwnClosesOnlyOwnSessions() {
        sessions.open(userId);
        UserId other = new UserId(UUID.randomUUID());
        sessions.open(other);
        UUID foreign = sessions.ofUser(other).get(0).id();

        assertThat(sessions.closeOwn(userId, foreign)).isFalse();
        assertThat(sessions.ofUser(other)).hasSize(1);
        assertThat(sessions.closeOwn(other, foreign)).isTrue();
    }

    @Test
    void resolveRenewsIdleTime() {
        String token = sessions.open(userId);
        clock.advance(Duration.ofMinutes(20));
        sessions.resolve(token);
        clock.advance(Duration.ofMinutes(20));

        assertThat(sessions.resolve(token)).contains(userId);
        assertThat(repository.findByTokenHash(secrets.hash(token)).orElseThrow().lastSeenAt())
                .isEqualTo(clock.instant());
    }

    @Test
    void closedTokenNoLongerResolves() {
        String token = sessions.open(userId);

        sessions.close(token);
        sessions.close(null);

        assertThat(sessions.resolve(token)).isEmpty();
    }

    @Test
    void closeAllOfClosesEverySessionOfThatUserOnly() {
        UserId other = new UserId(UUID.randomUUID());
        String first = sessions.open(userId);
        String second = sessions.open(userId);
        String foreign = sessions.open(other);

        sessions.closeAllOf(userId);

        assertThat(sessions.resolve(first)).isEmpty();
        assertThat(sessions.resolve(second)).isEmpty();
        assertThat(sessions.resolve(foreign)).contains(other);
    }
}
