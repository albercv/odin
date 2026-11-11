package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.identity.MemberChanges;
import com.e2d.odin.application.identity.MemberView;
import com.e2d.odin.application.identity.ResolveActor;
import com.e2d.odin.application.identity.StoredAdminSessions;
import com.e2d.odin.application.identity.UpdateMember;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UnauthorizedException;
import com.e2d.odin.domain.shared.UserId;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Una instalación de la fase 1.1 (hasta V6) con datos arranca con las migraciones nuevas y lo conserva. */
class MigrationFromPhaseOneIT {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID GROUP_ADMIN = UUID.randomUUID();
    private static final UUID COMPANY_ADMIN = UUID.randomUUID();
    private static final UUID ACTIVE_WORKER = UUID.randomUUID();
    private static final UUID INACTIVE_WORKER = UUID.randomUUID();
    private static final UUID DELETED_WORKER = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID DELETED_PROJECT = UUID.randomUUID();
    private static final UUID KEY = UUID.randomUUID();
    private static final UUID DELETED_WORKER_KEY = UUID.randomUUID();
    private static final String DELETED_AT = "2026-10-01T10:00:00Z";

    private static DataSource database;
    private static JdbcClient jdbc;

    @BeforeAll
    static void migrateFromPhaseOneToLatest() {
        database = Db.freshDatabase("migration_from_phase_one");
        Map<String, String> placeholders = Map.of("appPassword", Db.APP_PASSWORD);
        Flyway.configure().dataSource(database).placeholders(placeholders).target("6").load().migrate();
        jdbc = JdbcClient.create(database);
        insertPhaseOneData();
        Flyway.configure().dataSource(database).placeholders(placeholders).load().migrate();
    }

    private static void insertPhaseOneData() {
        jdbc.sql("INSERT INTO company (id, name) VALUES (:id, 'Empresa')").param("id", COMPANY).update();
        insertUser(GROUP_ADMIN, "GROUP_ADMIN", null, true);
        insertUser(COMPANY_ADMIN, "COMPANY_ADMIN", COMPANY, true);
        insertUser(ACTIVE_WORKER, "WORKER", COMPANY, true);
        insertUser(INACTIVE_WORKER, "WORKER", COMPANY, false);
        insertUser(DELETED_WORKER, "WORKER", COMPANY, true);
        jdbc.sql("UPDATE app_user SET deleted_at = :at WHERE id = :id")
                .param("at", OffsetDateTime.parse(DELETED_AT)).param("id", DELETED_WORKER).update();
        jdbc.sql("INSERT INTO project (id, company_id, name) VALUES (:id, :company, 'Obra')")
                .param("id", PROJECT).param("company", COMPANY).update();
        jdbc.sql("INSERT INTO project (id, company_id, name, deleted_at) VALUES (:id, :company, 'Vieja', now())")
                .param("id", DELETED_PROJECT).param("company", COMPANY).update();
        insertKey(KEY, ACTIVE_WORKER, "abcd1234", "h:secreto");
        insertKey(DELETED_WORKER_KEY, DELETED_WORKER, "wxyz5678", "h:otro");
    }

    private static void insertKey(UUID id, UUID user, String prefix, String secretHash) {
        jdbc.sql("""
                INSERT INTO access_key (id, user_id, label, prefix, secret_hash, created_at)
                VALUES (:id, :user, 'portatil', :prefix, :hash, now())""")
                .param("id", id).param("user", user).param("prefix", prefix).param("hash", secretHash).update();
    }

    private static void insertUser(UUID id, String role, UUID company, boolean active) {
        jdbc.sql("""
                INSERT INTO app_user (id, display_name, email, role, company_id, active)
                VALUES (:id, 'Persona', :email, :role, :company, :active)""")
                .param("id", id).param("email", id + "@x.test").param("role", role).param("company", company)
                .param("active", active).update();
    }

    private static Map<String, Object> membership(UUID user) {
        return jdbc.sql("""
                SELECT company_id, is_company_admin, is_worker, active, deleted_at
                FROM membership WHERE user_id = :user""").param("user", user).query().singleRow();
    }

    @Test
    void everyUserWithCompanyGetsOneMembershipWithItsRole() {
        assertThat(membership(COMPANY_ADMIN)).containsEntry("company_id", COMPANY)
                .containsEntry("is_company_admin", true).containsEntry("is_worker", false)
                .containsEntry("active", true).containsEntry("deleted_at", null);
        assertThat(membership(ACTIVE_WORKER)).containsEntry("company_id", COMPANY)
                .containsEntry("is_company_admin", false).containsEntry("is_worker", true)
                .containsEntry("active", true);
    }

    @Test
    void groupAdminIsFlaggedAndHasNoMembership() {
        List<UUID> flagged = jdbc.sql("SELECT id FROM app_user WHERE group_admin").query(UUID.class).list();

        assertThat(flagged).containsExactly(GROUP_ADMIN);
        assertThat(jdbc.sql("SELECT count(*) FROM membership WHERE user_id = :user").param("user", GROUP_ADMIN)
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM membership").query(Long.class).single()).isEqualTo(4);
    }

    @Test
    void accessKeyKeepsItsUserAndGainsItsCompany() {
        Map<String, Object> key = jdbc.sql("SELECT user_id, company_id FROM access_key WHERE id = :id")
                .param("id", KEY).query().singleRow();

        assertThat(key).containsEntry("user_id", ACTIVE_WORKER).containsEntry("company_id", COMPANY);
    }

    @Test
    void inactiveWorkerBecomesInactiveMembership() {
        assertThat(membership(INACTIVE_WORKER)).containsEntry("active", false).containsEntry("is_worker", true);
        assertThat(jdbc.sql("SELECT active FROM app_user WHERE id = :id").param("id", INACTIVE_WORKER)
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void softDeletedUserKeepsItsDeletionOnTheMembershipAndItsKeyGainsTheCompany() {
        assertThat(membership(DELETED_WORKER).get("deleted_at")).isEqualTo(
                Timestamp.from(OffsetDateTime.parse(DELETED_AT).toInstant()));
        Map<String, Object> key = jdbc.sql("SELECT user_id, company_id FROM access_key WHERE id = :id")
                .param("id", DELETED_WORKER_KEY).query().singleRow();
        assertThat(key).containsEntry("user_id", DELETED_WORKER).containsEntry("company_id", COMPANY);
    }

    @Test
    void reactivatingAMigratedInactiveWorkerLetsTheirKeyWorkAgain() {
        ScopedTransactions tx = new ScopedTransactions(database);
        PostgresUserRepository users = new PostgresUserRepository(tx);
        PostgresMembershipRepository memberships = new PostgresMembershipRepository(tx);
        PostgresAccessKeyRepository keys = new PostgresAccessKeyRepository(tx);
        FakeSecretHasher secrets = new FakeSecretHasher();
        StoredAdminSessions sessions = new StoredAdminSessions(new InMemoryAdminSessionRepository(), secrets,
                new SequenceTokenGenerator(), new SessionSettings(), new MutableClock());
        ResolveActor resolve = new ResolveActor(keys, users, memberships, sessions, secrets);
        UserId worker = new UserId(INACTIVE_WORKER);
        keys.save(new AccessKey(new AccessKeyId(UUID.randomUUID()), worker, new CompanyId(COMPANY), "otra", "inact123",
                secrets.hash("clave"), Instant.now(), Optional.empty()));
        assertThatThrownBy(() -> resolve.fromAccessKey("odin_inact123_clave"))
                .isInstanceOf(UnauthorizedException.class);

        try {
            MemberView updated = new UpdateMember(users, memberships, new FakePasswordHasher(), sessions).execute(
                    Actors.companyAdmin(new CompanyId(COMPANY)), new CompanyId(COMPANY), worker,
                    new MemberChanges(Optional.empty(), Optional.of(true), Optional.empty()));

            assertThat(updated.active()).isTrue();
            assertThat(resolve.fromAccessKey("odin_inact123_clave").userId()).isEqualTo(worker);
        } finally {
            memberships.save(memberships.find(worker, new CompanyId(COMPANY)).orElseThrow().withActive(false));
            jdbc.sql("DELETE FROM access_key WHERE prefix = 'inact123'").update();
        }
    }

    @Test
    void existingProjectsBecomeClosedWithTheirWorkersAsMembers() {
        assertThat(jdbc.sql("SELECT restricted FROM project").query(Boolean.class).list()).containsOnly(true);
        assertThat(jdbc.sql("SELECT user_id FROM project_member WHERE project_id = :id").param("id", PROJECT)
                .query(UUID.class).list()).containsExactly(ACTIVE_WORKER);
        assertThat(jdbc.sql("SELECT count(*) FROM project_member WHERE project_id = :id")
                .param("id", DELETED_PROJECT).query(Long.class).single()).isZero();
    }
}
