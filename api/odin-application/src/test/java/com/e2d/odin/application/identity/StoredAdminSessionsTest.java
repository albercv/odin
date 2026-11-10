package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.shared.UserId;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoredAdminSessionsTest {

    private final InMemoryAdminSessionRepository repository = new InMemoryAdminSessionRepository();
    private final FakeSecretHasher secrets = new FakeSecretHasher();
    private final MutableClock clock = new MutableClock();
    private final StoredAdminSessions sessions = new StoredAdminSessions(repository, secrets,
            new SequenceTokenGenerator(), new SessionSettings(Duration.ofHours(8)), clock);
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
        clock.advance(Duration.ofHours(8).plusSeconds(1));

        assertThat(sessions.resolve(token)).isEmpty();
        assertThat(repository.findByTokenHash(secrets.hash(token))).isEmpty();
    }

    @Test
    void resolveRenewsIdleTime() {
        String token = sessions.open(userId);
        clock.advance(Duration.ofHours(7));
        sessions.resolve(token);
        clock.advance(Duration.ofHours(7));

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
