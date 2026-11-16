package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.identity.ListMembers;
import com.e2d.odin.application.identity.MemberView;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresIdentityRepositoriesIT extends AbstractPostgresIT {

    private final PostgresAccessKeyRepository keys = new PostgresAccessKeyRepository(tx);
    private final PostgresAdminSessionRepository sessions = new PostgresAdminSessionRepository(tx);

    private User user(String email) {
        return new User(new UserId(UUID.randomUUID()), "Ana", Optional.of("Gil"), uniqueCode(),
                Optional.ofNullable(email), false, true, Optional.of("hash"));
    }

    private static User inactive(User u) {
        return u.withActive(false);
    }

    @Test
    void secondDeleteKeepsFirstTimestamp() throws Exception {
        User u = user("twice@x.com");
        users.save(u);
        Instant first = Instant.parse("2026-11-01T10:00:00Z");
        users.delete(u.id(), first);
        users.delete(u.id(), Instant.parse("2026-11-02T10:00:00Z"));
        try (var c = Db.owner().getConnection(); var st = c.prepareStatement(
                "SELECT deleted_at FROM app_user WHERE id = ?")) {
            st.setObject(1, u.id().value());
            var rs = st.executeQuery();
            rs.next();
            assertThat(rs.getTimestamp(1).toInstant()).isEqualTo(first);
        }
    }

    @Test
    void personCodeIsUniqueAmongDeletedToo() {
        User first = user("codigo@x.com");
        users.save(first);
        users.delete(first.id(), Instant.now());
        User second = new User(new UserId(UUID.randomUUID()), "Ana", Optional.of("Gil"), first.personCode(),
                Optional.empty(), false, true, Optional.empty());

        assertThat(users.personCodeExists(first.personCode())).isTrue();
        assertThat(users.personCodeExists("ZZZZ")).isFalse();
        assertThatThrownBy(() -> users.save(second)).isInstanceOf(ConflictException.class);
    }

    @Test
    void savesAndReadsFirstNameLastNameAndCode() {
        User withoutSurnames = new User(new UserId(UUID.randomUUID()), "Migrada", Optional.empty(), uniqueCode(),
                Optional.empty(), false, true, Optional.empty());
        users.save(withoutSurnames);
        User complete = user("c2@x.com");
        users.save(complete);

        assertThat(users.findById(withoutSurnames.id())).contains(withoutSurnames);
        assertThat(users.findById(complete.id()).orElseThrow().displayName()).isEqualTo("Ana Gil");
    }

    @Test
    void savesFindsAndUpdatesUsers() {
        User u = user("Ana@Example.com");
        users.save(u);
        assertThat(users.findById(u.id())).contains(u);
        assertThat(users.findByEmail("ana@example.COM")).contains(u);
        users.save(inactive(u));
        assertThat(users.findById(u.id()).orElseThrow().active()).isFalse();
    }

    @Test
    void softDeletedPersonDoesNotAppearInTheCompanyMemberListing() {
        CompanyId company = newCompany();
        UserId kept = newUser(company);
        UserId gone = newUser(company);
        ListMembers list = new ListMembers(users, memberships, orgs);
        Actor admin = new Actor(new UserId(UUID.randomUUID()), "Carlos", Role.COMPANY_ADMIN, Set.of(company));
        assertThat(list.execute(admin, company)).extracting(MemberView::userId).containsExactly(kept, gone);

        users.delete(gone, Instant.now());

        assertThat(list.execute(admin, company)).extracting(MemberView::userId).containsExactly(kept);
    }

    @Test
    void findsSeveralPeopleByIdInOneQueryIgnoringDeletedAndUnknown() {
        User first = user("uno@x.com");
        User second = user("dos@x.com");
        User deleted = user("tres@x.com");
        users.save(first);
        users.save(second);
        users.save(deleted);
        users.delete(deleted.id(), Instant.now());

        assertThat(users.findAllById(List.of(first.id(), second.id(), deleted.id(), new UserId(UUID.randomUUID()))))
                .containsExactlyInAnyOrder(first, second);
        assertThat(users.findAllById(List.of())).isEmpty();
    }

    @Test
    void emailIsUniqueIgnoringCaseButManyUsersMayHaveNone() {
        users.save(user("dup@x.com"));
        users.save(user(null));
        users.save(user(null));
        assertThatThrownBy(() -> users.save(user("DUP@x.com")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deletedUserIsExcludedFromReads() {
        User u = user("gone@x.com");
        users.save(u);
        users.delete(u.id(), Instant.now());
        assertThat(users.findById(u.id())).isEmpty();
        assertThat(users.findByEmail("gone@x.com")).isEmpty();
    }

    @Test
    void deletedUsersEmailIsFreeButInactiveUsersIsNot() {
        User deleted = user("free@x.com");
        User inactive = user("busy@x.com");
        users.save(deleted);
        users.save(inactive(inactive));
        users.delete(deleted.id(), Instant.now());
        users.save(user("free@x.com"));
        assertThatThrownBy(() -> users.save(user("busy@x.com")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void listsOnlyTheKeysOfThatMembershipsCompany() {
        CompanyId first = newCompany();
        CompanyId second = newCompany();
        UserId person = newUser(first);
        AccessKey inFirst = key(person, first, "mem1");
        AccessKey inSecond = key(person, second, "mem2");
        keys.save(inFirst);
        keys.save(inSecond);

        assertThat(keys.listByMembership(person, first)).containsExactly(inFirst);
        assertThat(keys.listByMembership(person, second)).containsExactly(inSecond);
        assertThat(keys.listByMembership(new UserId(UUID.randomUUID()), first)).isEmpty();
    }

    @Test
    void keysAreSavedFoundByPrefixAndListedPerUser() {
        CompanyId company = newCompany();
        UserId owner = newUser(company);
        UserId other = newUser(company);
        AccessKey k = key(owner, company, "pre1");
        keys.save(k);
        keys.save(key(other, company, "pre2"));
        assertThat(keys.findById(k.id())).contains(k);
        assertThat(keys.findByPrefix("pre1")).contains(k);
        assertThat(keys.findByPrefix("nope")).isEmpty();
        assertThat(keys.listByMembership(owner, company)).containsExactly(k);
        AccessKey revoked = new AccessKey(k.id(), owner, company, k.label(), k.prefix(), k.secretHash(), k.createdAt(),
                Optional.of(NOW));
        keys.save(revoked);
        assertThat(keys.findById(k.id())).contains(revoked);
    }

    @Test
    void keyPrefixIsUnique() {
        CompanyId company = newCompany();
        UserId owner = newUser(company);
        keys.save(key(owner, company, "same"));
        assertThatThrownBy(() -> keys.save(key(owner, company, "same"))).isInstanceOf(ConflictException.class);
    }

    @Test
    void sessionsAreSavedUpdatedAndDeleted() {
        UserId owner = newUser(newCompany());
        AdminSession s = new AdminSession("tok", owner, NOW, NOW);
        sessions.save(s);
        assertThat(sessions.findByTokenHash("tok")).contains(s);
        AdminSession seen = s.seenAt(NOW.plusSeconds(60));
        sessions.save(seen);
        assertThat(sessions.findByTokenHash("tok")).contains(seen);
        sessions.delete("tok");
        assertThat(sessions.findByTokenHash("tok")).isEmpty();
    }

    @Test
    void clientDataRoundTripsAndOwnSessionsAreListedAndClosedByIdOnlyForTheirOwner() {
        UserId owner = newUser(newCompany());
        UserId other = newUser(newCompany());
        AdminSession mine = new AdminSession(UUID.randomUUID(), "m", owner, NOW, NOW, Optional.of("Firefox"),
                Optional.of("10.0.0.7"), NOW);
        sessions.save(mine);
        sessions.save(new AdminSession("o", other, NOW, NOW));

        assertThat(sessions.findByTokenHash("m")).contains(mine);
        assertThat(sessions.findByUser(owner)).containsExactly(mine);
        UUID foreign = sessions.findByUser(other).get(0).id();
        assertThat(sessions.deleteByIdAndUser(foreign, owner)).isZero();
        assertThat(sessions.findByTokenHash("o")).isPresent();
        assertThat(sessions.deleteByIdAndUser(mine.id(), owner)).isEqualTo(1);
        assertThat(sessions.deleteByUser(other)).isEqualTo(1);
    }

    @Test
    void deleteByUserRemovesOnlyThatUsersSessions() {
        UserId company = newUser(newCompany());
        UserId other = newUser(newCompany());
        sessions.save(new AdminSession("a", company, NOW, NOW));
        sessions.save(new AdminSession("b", company, NOW, NOW));
        sessions.save(new AdminSession("c", other, NOW, NOW));

        sessions.deleteByUser(company);

        assertThat(sessions.findByTokenHash("a")).isEmpty();
        assertThat(sessions.findByTokenHash("b")).isEmpty();
        assertThat(sessions.findByTokenHash("c")).isPresent();
    }

    private static AccessKey key(UserId owner, CompanyId company, String prefix) {
        return new AccessKey(new AccessKeyId(UUID.randomUUID()), owner, company, "portatil", prefix, "secret-hash",
                Instant.parse("2026-11-09T10:00:00Z"), Optional.empty());
    }

    private User groupAdmin(String name, String email) {
        return new User(new UserId(UUID.randomUUID()), name, Optional.empty(), uniqueCode(), Optional.of(email), true,
                true, Optional.of("hash"));
    }

    @Test
    void listGroupAdminsReturnsEveryOneInOrderWithoutTheDeleted() {
        assertThat(users.listGroupAdmins()).isEmpty();
        User first = groupAdmin("Gema", "gema@x.com");
        User second = groupAdmin("Gael", "gael@x.com");
        User gone = groupAdmin("Gus", "gus@x.com");
        users.save(user("c@x.com"));
        users.save(first);
        users.save(second);
        users.save(gone);

        assertThat(users.listGroupAdmins()).containsExactly(first, second, gone);
        users.delete(gone.id(), Instant.now());
        assertThat(users.listGroupAdmins()).containsExactly(first, second);
    }

    @Test
    void findGroupAdminByEmailIgnoresCaseNonAdminsAndTheDeleted() {
        User admin = groupAdmin("Gema", "gema@x.com");
        User plain = user("c@x.com");
        users.save(plain);
        users.save(admin);

        assertThat(users.findGroupAdminByEmail("GEMA@x.com")).contains(admin);
        assertThat(users.findGroupAdminByEmail("c@x.com")).isEmpty();
        users.delete(admin.id(), Instant.now());
        assertThat(users.findGroupAdminByEmail("gema@x.com")).isEmpty();
    }
}
