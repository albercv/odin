package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryAccessKeyRepository;
import com.e2d.odin.application.fake.InMemoryAccessLinkRepository;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.SequencePersonCodeGenerator;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.port.AuditContext;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditActorKind;
import com.e2d.odin.domain.audit.AuditChannel;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.audit.AuditOutcome;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UnauthorizedException;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Una entrada por acción de administración de personas, claves, acceso e instalación, sin datos personales. */
class IdentityAuditTest {

    private static final String PASSWORD = "clave-larga-123";
    private static final String TOKEN = "token-secreto";

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final InMemoryProjectMemberRepository projectMembers = new InMemoryProjectMemberRepository(organizations);
    private final InMemoryAccessKeyRepository keys = new InMemoryAccessKeyRepository();
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final FakePasswordHasher passwords = new FakePasswordHasher();
    private final FakeSecretHasher secrets = new FakeSecretHasher();
    private final MutableClock clock = new MutableClock();
    private final StoredAdminSessions sessions = new StoredAdminSessions(new InMemoryAdminSessionRepository(), secrets,
            new SequenceTokenGenerator(), new SessionSettings(), clock);
    private final CompanyId company = Fixtures.company(organizations);
    private final Actor group = Actors.groupAdmin();
    private final Actor admin = Actors.companyAdmin(company);
    private final AuditContext panel = () -> new AuditContext.AuditRequest(AuditChannel.PANEL, Optional.empty(),
            Optional.of("10.0.0.7"), Optional.of("Firefox"));

    private AuditEntry only(AuditAction action) {
        assertThat(audit.entries).singleElement();
        AuditEntry entry = audit.entries.get(0);
        assertThat(entry.action()).isEqualTo(action);
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.DONE);
        return entry;
    }

    private User worker(String email) {
        return People.member(users, memberships, new UserId(UUID.randomUUID()), "Wanda", Optional.ofNullable(email),
                Role.WORKER, company, true, Optional.empty());
    }

    private static AuditEntry.Target user(User u) {
        return new AuditEntry.Target("USER", u.id().value().toString(), null);
    }

    @Test
    void addGroupAdminRecordsThePerson() {
        GroupAdminAdded added = new AddGroupAdmin(users, memberships, passwords, new SequenceIdGenerator(), new SequencePersonCodeGenerator("7F3K"), audit)
                .execute(group, new NewGroupAdmin("Ana", "Pérez", "ana@demo.test", Optional.of(PASSWORD)));

        AuditEntry e = only(AuditAction.GROUP_ADMIN_ADDED);
        assertThat(e.actorUserId()).contains(group.userId());
        assertThat(e.company()).isEmpty();
        assertThat(e.target()).contains(user(added.admin()));
        assertThat(e.details()).isEqualTo(Map.of("alreadyExisted", false));
    }

    @Test
    void revokeGroupAdminRecordsThePerson() {
        User other = People.groupAdmin(users, new UserId(UUID.randomUUID()), "Otro", Optional.of("o@demo.test"), true,
                Optional.of("pw:x"));
        People.groupAdmin(users, group.userId(), "Gema", Optional.of("g@demo.test"), true, Optional.of("pw:y"));

        new RevokeGroupAdmin(users, memberships, sessions, audit).execute(group, other.id());

        AuditEntry e = only(AuditAction.GROUP_ADMIN_REVOKED);
        assertThat(e.target()).contains(user(other));
        assertThat(e.company()).isEmpty();
    }

    @Test
    void addMemberRecordsRolesInTheCompany() {
        MemberAdded added = new AddMember(users, memberships, organizations, projectMembers, passwords,
                new SequenceIdGenerator(), new SequencePersonCodeGenerator("7F3K"), audit).execute(admin, company,
                new NewMember("Pepe", "Gómez", Optional.of("p@demo.test"), Set.of(Role.WORKER), Optional.empty(),
                        Set.of()));

        AuditEntry e = only(AuditAction.MEMBER_ADDED);
        assertThat(e.actorRole()).contains("COMPANY_ADMIN");
        assertThat(e.company()).contains(company);
        assertThat(e.target()).contains(user(added.user()));
        assertThat(e.details()).containsEntry("roles", List.of("WORKER")).containsEntry("alreadyExisted", false);
        assertThat(e.details().toString()).doesNotContain("p@demo.test", "Pepe");
    }

    @Test
    void updateMemberRecordsBeforeAndAfter() {
        User wanda = worker(null);

        new UpdateMember(users, memberships, organizations, passwords, sessions, audit).execute(admin, company,
                wanda.id(), new MemberChanges(Optional.empty(), Optional.of(false), Optional.empty()));

        AuditEntry e = only(AuditAction.MEMBER_UPDATED);
        assertThat(e.company()).contains(company);
        assertThat(e.target()).contains(user(wanda));
        assertThat(e.details()).containsEntry("before", Map.of("roles", List.of("WORKER"), "active", true))
                .containsEntry("after", Map.of("roles", List.of("WORKER"), "active", false));
    }

    @Test
    void removeMemberRecordsThePersonInTheCompany() {
        User wanda = worker(null);

        new RemoveMember(users, memberships, keys, projectMembers, sessions, clock, audit).execute(admin, company,
                wanda.id());

        AuditEntry e = only(AuditAction.MEMBER_REMOVED);
        assertThat(e.company()).contains(company);
        assertThat(e.target()).contains(user(wanda));
    }

    @Test
    void updateUserRecordsFlagsNotNamesNorEmails() {
        User wanda = worker("wanda@demo.test");

        new UpdateUser(users, memberships, sessions, audit).execute(group, wanda.id(), new UserChanges(
                Optional.of("Wanda María"), Optional.empty(), Optional.of("nueva@demo.test"), Optional.of(false)));

        AuditEntry e = only(AuditAction.USER_UPDATED);
        assertThat(e.company()).isEmpty();
        assertThat(e.target()).contains(user(wanda));
        assertThat(e.details()).containsEntry("nameChanged", true).containsEntry("emailChanged", true)
                .containsEntry("before", Map.of("active", true)).containsEntry("after", Map.of("active", false));
        assertThat(e.details().toString()).doesNotContain("demo.test", "Wanda");
    }

    @Test
    void updateUserWithoutActiveChangeHasNoBeforeAndAfter() {
        User wanda = worker("wanda@demo.test");

        new UpdateUser(users, memberships, sessions, audit).execute(group, wanda.id(), new UserChanges(
                Optional.empty(), Optional.of("Gómez"), Optional.empty(), Optional.empty()));

        assertThat(only(AuditAction.USER_UPDATED).details())
                .isEqualTo(Map.of("nameChanged", true, "emailChanged", false));
    }

    @Test
    void deleteUserRecordsThePerson() {
        User wanda = worker(null);

        new DeleteUser(users, memberships, projectMembers, sessions, clock, audit).execute(group, wanda.id());

        assertThat(only(AuditAction.USER_DELETED).target()).contains(user(wanda));
    }

    @Test
    void changePasswordRecordsNoPassword() {
        User carlos = People.member(users, memberships, new UserId(UUID.randomUUID()), "Carlos",
                Optional.of("c@demo.test"), Role.COMPANY_ADMIN, company, true, Optional.of("pw:vieja-clave-123"));

        sessions.open(carlos.id());
        sessions.open(carlos.id());
        new ChangePassword(users, memberships, passwords, sessions, audit).execute(group, carlos.id(), Optional.empty(),
                "clave-nueva-12345");

        AuditEntry e = only(AuditAction.PASSWORD_CHANGED);
        assertThat(e.target()).contains(user(carlos));
        assertThat(e.details()).isEqualTo(Map.of("own", false, "sessionsClosed", 2));
    }

    @Test
    void closingAnOwnSessionRecordsSessionClosedWithoutClientData() {
        sessions.open(group.userId(), panel.current());
        UUID id = sessions.ofUser(group.userId()).get(0).id();

        new OwnAdminSessions(sessions, new SessionSettings(), audit).close(group, id);

        AuditEntry e = only(AuditAction.SESSION_CLOSED);
        assertThat(e.target()).contains(new AuditEntry.Target("SESSION", id.toString(), null));
        assertThat(e.details()).isEmpty();
    }

    @Test
    void closingAForeignOrUnknownSessionIs404AndLeavesNoEntryNorChange() {
        sessions.open(admin.userId());
        UUID foreign = sessions.ofUser(admin.userId()).get(0).id();
        OwnAdminSessions own = new OwnAdminSessions(sessions, new SessionSettings(), audit);

        assertThatThrownBy(() -> own.close(group, foreign)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> own.close(group, UUID.randomUUID())).isInstanceOf(NotFoundException.class);

        assertThat(sessions.ofUser(admin.userId())).hasSize(1);
        assertThat(audit.entries).isEmpty();
    }

    @Test
    void listMarksTheCurrentSessionAndShowsOnlyOwn() {
        String mine = sessions.open(group.userId(), panel.current());
        sessions.open(group.userId());
        sessions.open(admin.userId());

        var listed = new OwnAdminSessions(sessions, new SessionSettings(), audit).list(group, mine);

        assertThat(listed).hasSize(2);
        assertThat(listed).filteredOn(OwnAdminSessions.Listed::current).singleElement()
                .satisfies(l -> assertThat(l.session().ip()).contains("10.0.0.7"));
    }

    @Test
    void issuingAKeyRecordsOnlyThePrefix() {
        User wanda = worker(null);

        IssuedKey issued = new IssueAccessKey(keys, users, memberships, secrets, new SequenceTokenGenerator(),
                new SequenceIdGenerator(), clock, audit).execute(admin, company, wanda.id(), "portátil de Wanda");

        AuditEntry e = only(AuditAction.ACCESS_KEY_ISSUED);
        assertThat(e.company()).contains(company);
        assertThat(e.target()).contains(new AuditEntry.Target("ACCESS_KEY", issued.id().value().toString(), null));
        assertThat(e.details()).isEqualTo(Map.of("prefix", issued.prefix(), "workerId", wanda.id().value().toString()));
        assertThat(e.details().toString()).doesNotContain(issued.plainKey()).doesNotContain("portátil");
    }

    @Test
    void revokingAKeyRecordsOnlyThePrefix() {
        User wanda = worker(null);
        AccessKeyId id = new AccessKeyId(UUID.randomUUID());
        keys.save(new AccessKey(id, wanda.id(), company, "etiqueta", "abcdefgh", "h:secreto", clock.instant(),
                Optional.empty()));

        new RevokeAccessKey(keys, users, new InMemoryAccessLinkRepository(), clock, audit).execute(admin, id);

        AuditEntry e = only(AuditAction.ACCESS_KEY_REVOKED);
        assertThat(e.company()).contains(company);
        assertThat(e.details()).isEqualTo(Map.of("prefix", "abcdefgh", "workerId", wanda.id().value().toString()));
    }

    private LogInAdmin logIn() {
        return new LogInAdmin(users, memberships, passwords, sessions, audit, panel);
    }

    private User savedGroupAdmin() {
        return People.groupAdmin(users, new UserId(UUID.randomUUID()), "Gema", Optional.of("g@demo.test"), true,
                Optional.of(passwords.hash(PASSWORD)));
    }

    @Test
    void successfulLoginRecordsClientData() {
        User gema = savedGroupAdmin();

        logIn().execute("g@demo.test", PASSWORD);

        AuditEntry e = only(AuditAction.LOGIN_SUCCEEDED);
        assertThat(e.actorUserId()).contains(gema.id());
        assertThat(e.actorRole()).contains("GROUP_ADMIN");
        assertThat(e.company()).isEmpty();
        assertThat(e.details()).isEqualTo(Map.of("ip", "10.0.0.7", "userAgent", "Firefox"));
        assertThat(sessions.ofUser(gema.id())).singleElement().satisfies(stored -> {
            assertThat(stored.ip()).contains("10.0.0.7");
            assertThat(stored.userAgent()).contains("Firefox");
        });
    }

    @Test
    void failedLoginOfKnownUserIsDeniedWithTheUserAndNoEmail() {
        User gema = savedGroupAdmin();

        assertThatThrownBy(() -> logIn().execute("g@demo.test", "mala")).isInstanceOf(UnauthorizedException.class);

        assertThat(audit.entries).isEmpty();
        assertThat(audit.denied).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo(AuditAction.LOGIN_FAILED);
            assertThat(e.outcome()).isEqualTo(AuditOutcome.DENIED);
            assertThat(e.actorUserId()).contains(gema.id());
            assertThat(e.details()).isEqualTo(Map.of("knownUser", true, "ip", "10.0.0.7", "userAgent", "Firefox"));
        });
    }

    @Test
    void failedLoginOfUnknownEmailHasNoUserNorEmail() {
        assertThatThrownBy(() -> logIn().execute("nadie@demo.test", PASSWORD)).isInstanceOf(UnauthorizedException.class);

        assertThat(audit.denied).singleElement().satisfies(e -> {
            assertThat(e.actorUserId()).isEmpty();
            assertThat(e.details()).isEqualTo(Map.of("knownUser", false, "ip", "10.0.0.7", "userAgent", "Firefox"));
            assertThat(e.details().toString()).doesNotContain("nadie");
        });
    }

    private SetUpInstallation setUp() {
        return new SetUpInstallation(organizations, users, passwords, new SequenceIdGenerator(),
                new SequencePersonCodeGenerator("7F3K"), new InstallationSettings(TOKEN), audit);
    }

    @Test
    void setupRecordsTheInstallerAndTheNewAdmin() {
        UserId id = setUp().execute(TOKEN, "Grupo", "Gema", "g@demo.test", PASSWORD);

        AuditEntry e = only(AuditAction.INSTALLATION_SET_UP);
        assertThat(e.actorKind()).isEqualTo(AuditActorKind.INSTALLER);
        assertThat(e.actorUserId()).isEmpty();
        assertThat(e.target()).contains(new AuditEntry.Target("USER", id.value().toString(), null));
    }

    @Test
    void setupWithWrongTokenIsDeniedAsInstaller() {
        assertThatThrownBy(() -> setUp().execute("otro", "Grupo", "Gema", "g@demo.test", PASSWORD))
                .isInstanceOf(ForbiddenException.class);

        assertThat(audit.entries).isEmpty();
        assertThat(audit.denied).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo(AuditAction.INSTALLATION_SET_UP);
            assertThat(e.outcome()).isEqualTo(AuditOutcome.DENIED);
            assertThat(e.actorKind()).isEqualTo(AuditActorKind.INSTALLER);
        });
    }

    @Test
    void recoverRecordsTheInstallerAndTheAdmin() {
        User gema = savedGroupAdmin();

        new RecoverGroupAdmin(users, passwords, sessions, new InstallationSettings(TOKEN), audit).execute(TOKEN,
                Optional.empty(), "clave-nueva-12345");

        AuditEntry e = only(AuditAction.ADMIN_PASSWORD_RECOVERED);
        assertThat(e.actorKind()).isEqualTo(AuditActorKind.INSTALLER);
        assertThat(e.target()).contains(user(gema));
    }

    @Test
    void recoverWithWrongTokenIsDenied() {
        savedGroupAdmin();

        assertThatThrownBy(() -> new RecoverGroupAdmin(users, passwords, sessions, new InstallationSettings(TOKEN),
                audit).execute("otro", Optional.empty(), "clave-nueva-12345")).isInstanceOf(ForbiddenException.class);

        assertThat(audit.entries).isEmpty();
        assertThat(audit.denied).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo(AuditAction.ADMIN_PASSWORD_RECOVERED);
            assertThat(e.outcome()).isEqualTo(AuditOutcome.DENIED);
        });
    }
}
