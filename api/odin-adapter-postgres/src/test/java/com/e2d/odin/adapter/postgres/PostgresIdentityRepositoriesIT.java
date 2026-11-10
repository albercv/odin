package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresIdentityRepositoriesIT extends AbstractPostgresIT {

    private final PostgresAccessKeyRepository keys = new PostgresAccessKeyRepository(tx);
    private final PostgresAdminSessionRepository sessions = new PostgresAdminSessionRepository(tx);

    private User user(String email, Optional<CompanyId> company) {
        return new User(new UserId(UUID.randomUUID()), "Ana", Optional.ofNullable(email), Role.COMPANY_ADMIN, company,
                true, Optional.of("hash"));
    }

    private static User inactive(User u) {
        return u.updated(u.displayName(), u.email(), false);
    }

    @Test
    void secondDeleteKeepsFirstTimestamp() throws Exception {
        User u = user("twice@x.com", Optional.empty());
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
        CompanyId company = newCompany();
        User u = user("Ana@Example.com", Optional.of(company));
        users.save(u);
        assertThat(users.findById(u.id())).contains(u);
        assertThat(users.findByEmail("ana@example.COM")).contains(u);
        users.save(inactive(u));
        assertThat(users.findById(u.id()).orElseThrow().active()).isFalse();
    }

    @Test
    void groupAdminHasNoCompanyAndListByCompanyOnlyReturnsItsUsers() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        User admin = user(null, Optional.empty());
        User ua = user("a@x.com", Optional.of(a));
        User ub = user("b@x.com", Optional.of(b));
        users.save(admin);
        users.save(ua);
        users.save(ub);
        assertThat(users.findById(admin.id())).contains(admin);
        assertThat(users.listByCompany(a)).containsExactly(ua);
    }

    @Test
    void emailIsUniqueIgnoringCaseButManyUsersMayHaveNone() {
        users.save(user("dup@x.com", Optional.empty()));
        users.save(user(null, Optional.empty()));
        users.save(user(null, Optional.empty()));
        assertThatThrownBy(() -> users.save(user("DUP@x.com", Optional.empty())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deletedUserIsExcludedFromReads() {
        CompanyId company = newCompany();
        User u = user("gone@x.com", Optional.of(company));
        users.save(u);
        users.delete(u.id(), Instant.now());
        assertThat(users.findById(u.id())).isEmpty();
        assertThat(users.findByEmail("gone@x.com")).isEmpty();
        assertThat(users.listByCompany(company)).isEmpty();
    }

    @Test
    void deletedUsersEmailIsFreeButInactiveUsersIsNot() {
        User deleted = user("free@x.com", Optional.empty());
        User inactive = user("busy@x.com", Optional.empty());
        users.save(deleted);
        users.save(inactive(inactive));
        users.delete(deleted.id(), Instant.now());
        users.save(user("free@x.com", Optional.empty()));
        assertThatThrownBy(() -> users.save(user("busy@x.com", Optional.empty())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void keysAreSavedFoundByPrefixAndListedPerUser() {
        UserId owner = newUser(newCompany());
        UserId other = newUser(newCompany());
        AccessKey k = key(owner, "pre1");
        keys.save(k);
        keys.save(key(other, "pre2"));
        assertThat(keys.findById(k.id())).contains(k);
        assertThat(keys.findByPrefix("pre1")).contains(k);
        assertThat(keys.findByPrefix("nope")).isEmpty();
        assertThat(keys.listByUser(owner)).containsExactly(k);
        AccessKey revoked = new AccessKey(k.id(), owner, k.label(), k.prefix(), k.secretHash(), k.createdAt(),
                Optional.of(NOW));
        keys.save(revoked);
        assertThat(keys.findById(k.id())).contains(revoked);
    }

    @Test
    void keyPrefixIsUnique() {
        UserId owner = newUser(newCompany());
        keys.save(key(owner, "same"));
        assertThatThrownBy(() -> keys.save(key(owner, "same"))).isInstanceOf(ConflictException.class);
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

    private static AccessKey key(UserId owner, String prefix) {
        return new AccessKey(new AccessKeyId(UUID.randomUUID()), owner, "portatil", prefix, "secret-hash", Instant.parse(
                "2026-11-09T10:00:00Z"), Optional.empty());
    }
}
