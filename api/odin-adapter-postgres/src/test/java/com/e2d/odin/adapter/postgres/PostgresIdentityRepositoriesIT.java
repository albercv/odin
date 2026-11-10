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
        return new User(new UserId(UUID.randomUUID()), "Ana", Optional.ofNullable(email), false, true,
                Optional.of("hash"));
    }

    private static User inactive(User u) {
        return u.updated(u.displayName(), u.email(), false);
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
        ListMembers list = new ListMembers(users, memberships);
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
        assertThat(keys.listByUser(owner)).containsExactly(k);
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
        AdminSession seen = new AdminSession("tok", owner, NOW, NOW.plusSeconds(60));
        sessions.save(seen);
        assertThat(sessions.findByTokenHash("tok")).contains(seen);
        sessions.delete("tok");
        assertThat(sessions.findByTokenHash("tok")).isEmpty();
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

    @Test
    void findGroupAdminReturnsTheOnlyGroupAdmin() {
        assertThat(users.findGroupAdmin()).isEmpty();
        User admin = new User(new UserId(UUID.randomUUID()), "Gema", Optional.of("gema@x.com"), true, true,
                Optional.of("hash"));
        users.save(user("c@x.com"));
        users.save(admin);
        assertThat(users.findGroupAdmin()).contains(admin);
        users.delete(admin.id(), Instant.now());
        assertThat(users.findGroupAdmin()).isEmpty();
    }
}
