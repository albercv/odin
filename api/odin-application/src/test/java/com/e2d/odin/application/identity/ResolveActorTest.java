package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAccessKeyRepository;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UnauthorizedException;
import com.e2d.odin.domain.shared.UserId;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResolveActorTest {

    private static final String PLAIN_KEY = "odin_abcdefgh_secreto";
    private static final String MESSAGE = "Credenciales no válidas";

    private final InMemoryAccessKeyRepository keys = new InMemoryAccessKeyRepository();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryAdminSessionRepository sessions = new InMemoryAdminSessionRepository();
    private final FakeSecretHasher secrets = new FakeSecretHasher();
    private final MutableClock clock = new MutableClock();
    private final ResolveActor resolve = new ResolveActor(keys, users, sessions, secrets,
            new SessionSettings(Duration.ofHours(8)), clock);
    private final UserId workerId = new UserId(UUID.randomUUID());
    private final AccessKeyId keyId = new AccessKeyId(UUID.randomUUID());

    private void givenWorker(boolean active) {
        users.save(new User(workerId, "Wanda", Optional.empty(), Role.WORKER,
                Optional.of(new CompanyId(UUID.randomUUID())), active, Optional.empty()));
        keys.save(new AccessKey(keyId, workerId, "portátil", "abcdefgh", "h:secreto", clock.instant(),
                Optional.empty()));
    }

    private String givenSession() {
        UserId adminId = new UserId(UUID.randomUUID());
        users.save(new User(adminId, "Gema", Optional.of("g@demo.test"), Role.GROUP_ADMIN, Optional.empty(), true,
                Optional.of("pw:x")));
        sessions.save(new AdminSession(secrets.hash("tok"), adminId, clock.instant(), clock.instant()));
        return "tok";
    }

    @Test
    void resolvesWorkerFromKey() {
        givenWorker(true);

        Actor actor = resolve.fromAccessKey(PLAIN_KEY);

        assertThat(actor.userId()).isEqualTo(workerId);
        assertThat(actor.role()).isEqualTo(Role.WORKER);
    }

    @Test
    void rejectsRevokedKey() {
        givenWorker(true);
        keys.save(keys.findById(keyId).orElseThrow().revoked(clock.instant()));

        assertThatThrownBy(() -> resolve.fromAccessKey(PLAIN_KEY))
                .isInstanceOf(UnauthorizedException.class).hasMessage(MESSAGE);
    }

    @Test
    void rejectsDeactivatedUserKey() {
        givenWorker(false);

        assertThatThrownBy(() -> resolve.fromAccessKey(PLAIN_KEY))
                .isInstanceOf(UnauthorizedException.class).hasMessage(MESSAGE);
    }

    @Test
    void rejectsUnknownOrMalformedKey() {
        givenWorker(true);

        for (String bad : new String[] {"odin_abcdefgh_otro", "odin_zzzzzzzz_secreto", "basura", "", "odin_"}) {
            assertThatThrownBy(() -> resolve.fromAccessKey(bad))
                    .isInstanceOf(UnauthorizedException.class).hasMessage(MESSAGE);
        }
    }

    @Test
    void rejectsExpiredSession() {
        String token = givenSession();
        clock.advance(Duration.ofHours(8).plusSeconds(1));

        assertThatThrownBy(() -> resolve.fromSessionToken(token))
                .isInstanceOf(UnauthorizedException.class).hasMessage(MESSAGE);
    }

    @Test
    void refreshesLastSeen() {
        String token = givenSession();
        clock.advance(Duration.ofHours(7));
        resolve.fromSessionToken(token);
        clock.advance(Duration.ofHours(7));

        Actor actor = resolve.fromSessionToken(token);

        assertThat(actor.role()).isEqualTo(Role.GROUP_ADMIN);
        assertThat(sessions.findByTokenHash(secrets.hash(token)).orElseThrow().lastSeenAt())
                .isEqualTo(clock.instant());
    }

    @Test
    void rejectsUnknownSession() {
        assertThatThrownBy(() -> resolve.fromSessionToken("nada"))
                .isInstanceOf(UnauthorizedException.class).hasMessage(MESSAGE);
    }

    @Test
    void sessionOfDeactivatedUserIsRejected() {
        String token = givenSession();
        UserId adminId = sessions.findByTokenHash(secrets.hash(token)).orElseThrow().userId();
        users.save(users.findById(adminId).orElseThrow().deactivated());

        assertThatThrownBy(() -> resolve.fromSessionToken(token))
                .isInstanceOf(UnauthorizedException.class).hasMessage(MESSAGE);
    }
}
