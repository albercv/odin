package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
    private static final UUID KEY = UUID.randomUUID();

    private static JdbcClient jdbc;

    @BeforeAll
    static void migrateFromPhaseOneToLatest() {
        DataSource database = Db.freshDatabase("migration_from_phase_one");
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
        jdbc.sql("""
                INSERT INTO access_key (id, user_id, label, prefix, secret_hash, created_at)
                VALUES (:id, :user, 'portatil', 'abcd1234', 'hash', now())""")
                .param("id", KEY).param("user", ACTIVE_WORKER).update();
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
        assertThat(jdbc.sql("SELECT count(*) FROM membership").query(Long.class).single()).isEqualTo(3);
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
    }
}
