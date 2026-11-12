package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAccessKeyRepository;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UnauthorizedException;
import com.e2d.odin.domain.shared.UserId;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResolveActorTest {

    private static final String PLAIN_KEY = "odin_abcdefgh_secreto";
    private static final String MESSAGE = "Credenciales no válidas";

    private final InMemoryAccessKeyRepository keys = new InMemoryAccessKeyRepository();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final InMemoryAdminSessionRepository sessions = new InMemoryAdminSessionRepository();
    private final FakeSecretHasher secrets = new FakeSecretHasher();
    private final MutableClock clock = new MutableClock();
    private final ResolveActor resolve = new ResolveActor(keys, users, memberships,
            new StoredAdminSessions(sessions, secrets, new SequenceTokenGenerator(),
                    new SessionSettings(Duration.ofHours(8)), clock), secrets);
    private final UserId workerId = new UserId(UUID.randomUUID());
    private final AccessKeyId keyId = new AccessKeyId(UUID.randomUUID());
    private final CompanyId company = new CompanyId(UUID.randomUUID());

    private void givenWorker(boolean active) {
        givenWorker(active, true);
    }

    private void givenWorker(boolean personActive, boolean membershipActive) {
        users.save(new User(workerId, "Wanda", Optional.of("Pérez"), People.nextCode(), Optional.empty(), false, personActive, Optional.empty()));
        memberships.save(new Membership(workerId, company, Set.of(Role.WORKER), membershipActive));
        keys.save(new AccessKey(keyId, workerId, company, "portátil", "abcdefgh", "h:secreto", clock.instant(),
                Optional.empty()));
    }

    private String givenSession() {
        UserId adminId = new UserId(UUID.randomUUID());
        People.groupAdmin(users, adminId, "Gema", Optional.of("g@demo.test"), true, Optional.of("pw:x"));
        return openSession(adminId);
    }

    private String openSession(UserId userId) {
        sessions.save(new AdminSession(secrets.hash("tok"), userId, clock.instant(), clock.instant()));
        return "tok";
    }

    @Test
    void groupAdminWithAWorkerMembershipWorksWithTheirKeyAndStaysGroupAdminInThePanel() {
        People.groupAdmin(users, workerId, "Gema", Optional.of("g@demo.test"), true, Optional.of("pw:x"));
        memberships.save(new Membership(workerId, company, Set.of(Role.WORKER), true));
        keys.save(new AccessKey(keyId, workerId, company, "portátil", "abcdefgh", "h:secreto", clock.instant(),
                Optional.empty()));

        Actor worker = resolve.fromAccessKey(PLAIN_KEY);
        Actor panel = resolve.fromSessionToken(openSession(workerId));

        assertThat(worker).isEqualTo(new Actor(workerId, "Gema Pérez", Role.WORKER, Set.of(company)));
        assertThat(panel.role()).isEqualTo(Role.GROUP_ADMIN);
    }

    @Test
    void resolvesWorkerFromKey() {
        givenWorker(true);

        Actor actor = resolve.fromAccessKey(PLAIN_KEY);

        assertThat(actor.userId()).isEqualTo(workerId);
        assertThat(actor.role()).isEqualTo(Role.WORKER);
        assertThat(actor.companies()).containsExactly(company);
    }

    @Test
    void keyOfInactiveMembershipIsUnauthorized() {
        givenWorker(true, false);

        assertThatThrownBy(() -> resolve.fromAccessKey(PLAIN_KEY))
                .isInstanceOf(UnauthorizedException.class).hasMessage(MESSAGE);
    }

    @Test
    void keyOfMembershipWithoutWorkerRoleIsUnauthorized() {
        givenWorker(true);
        memberships.save(new Membership(workerId, company, Set.of(Role.COMPANY_ADMIN), true));

        assertThatThrownBy(() -> resolve.fromAccessKey(PLAIN_KEY))
                .isInstanceOf(UnauthorizedException.class).hasMessage(MESSAGE);
    }

    @Test
    void keyOfAnotherCompanyThanTheMembershipIsUnauthorized() {
        givenWorker(true);
        AccessKey key = keys.findById(keyId).orElseThrow();
        keys.save(new AccessKey(keyId, workerId, new CompanyId(UUID.randomUUID()), key.label(), key.prefix(),
                key.secretHash(), key.createdAt(), Optional.empty()));

        assertThatThrownBy(() -> resolve.fromAccessKey(PLAIN_KEY))
                .isInstanceOf(UnauthorizedException.class).hasMessage(MESSAGE);
    }

    @Test
    void sessionOfUserWithoutAdminMembershipIsUnauthorized() {
        givenWorker(true);
        String token = openSession(workerId);

        assertThatThrownBy(() -> resolve.fromSessionToken(token))
                .isInstanceOf(UnauthorizedException.class).hasMessage(MESSAGE);
    }

    @Test
    void sessionOfCompanyAdminActsInTheCompaniesItAdministers() {
        UserId adminId = new UserId(UUID.randomUUID());
        People.member(users, memberships, adminId, "Carlos", Optional.of("c@demo.test"), Role.COMPANY_ADMIN, company,
                true, Optional.of("pw:x"));

        Actor actor = resolve.fromSessionToken(openSession(adminId));

        assertThat(actor.role()).isEqualTo(Role.COMPANY_ADMIN);
        assertThat(actor.companies()).containsExactly(company);
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
        User admin = users.findById(adminId).orElseThrow();
        users.save(admin.withActive(false));

        assertThatThrownBy(() -> resolve.fromSessionToken(token))
                .isInstanceOf(UnauthorizedException.class).hasMessage(MESSAGE);
    }

    @Test
    void deletedWorkersKeyIsUnauthorized() {
        givenWorker(true);
        users.delete(workerId, clock.instant());

        assertThatThrownBy(() -> resolve.fromAccessKey(PLAIN_KEY))
                .isInstanceOf(UnauthorizedException.class).hasMessage(MESSAGE);
    }

    @Test
    void deletedAdminsSessionIsUnauthorized() {
        String token = givenSession();
        users.delete(sessions.findByTokenHash(secrets.hash(token)).orElseThrow().userId(), clock.instant());

        assertThatThrownBy(() -> resolve.fromSessionToken(token))
                .isInstanceOf(UnauthorizedException.class).hasMessage(MESSAGE);
    }

    @Test
    void keyStopsWorkingWhenMembershipLosesWorkerRole() {
        givenWorker(true);
        assertThat(resolve.fromAccessKey(PLAIN_KEY).role()).isEqualTo(Role.WORKER);

        memberships.save(new Membership(workerId, company, Set.of(Role.COMPANY_ADMIN), true));

        assertThatThrownBy(() -> resolve.fromAccessKey(PLAIN_KEY))
                .isInstanceOf(UnauthorizedException.class).hasMessage(MESSAGE);
    }

    @Test
    void keyOfCompanyAResolvesToCompanyAOnly() {
        givenWorker(true);
        CompanyId companyB = new CompanyId(UUID.randomUUID());
        memberships.save(new Membership(workerId, companyB, Set.of(Role.WORKER), true));
        keys.save(new AccessKey(new AccessKeyId(UUID.randomUUID()), workerId, companyB, "otro", "ijklmnop",
                "h:otro-secreto", clock.instant(), Optional.empty()));

        assertThat(resolve.fromAccessKey(PLAIN_KEY).companies()).containsExactly(company);
        assertThat(resolve.fromAccessKey("odin_ijklmnop_otro-secreto").companies()).containsExactly(companyB);
    }

    @Test
    void adminOfTwoCompaniesGetsBothInActor() {
        UserId adminId = new UserId(UUID.randomUUID());
        CompanyId companyB = new CompanyId(UUID.randomUUID());
        People.member(users, memberships, adminId, "Carlos", Optional.of("c@demo.test"), Role.COMPANY_ADMIN, company,
                true, Optional.of("pw:x"));
        memberships.save(new Membership(adminId, companyB, Set.of(Role.COMPANY_ADMIN, Role.WORKER), true));

        Actor actor = resolve.fromSessionToken(openSession(adminId));

        assertThat(actor.role()).isEqualTo(Role.COMPANY_ADMIN);
        assertThat(actor.companies()).containsExactlyInAnyOrder(company, companyB);
    }
}
