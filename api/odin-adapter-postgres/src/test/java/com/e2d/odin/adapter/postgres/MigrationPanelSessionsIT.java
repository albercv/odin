package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** V32: las sesiones del panel que ya estaban abiertas siguen valiendo, con id y sin navegador ni IP. */
class MigrationPanelSessionsIT {

    @Test
    void sessionsOpenedBeforeV32GetAnIdAndReauthenticatedAtEqualToCreatedAt() {
        DataSource database = Db.freshDatabase("migration_panel_sessions");
        Map<String, String> placeholders = Map.of("appPassword", Db.APP_PASSWORD);
        Flyway.configure().dataSource(database).placeholders(placeholders).target("30").load().migrate();
        JdbcClient jdbc = JdbcClient.create(database);
        UUID user = UUID.randomUUID();
        Instant created = Instant.parse("2026-11-16T08:00:00Z");
        jdbc.sql("INSERT INTO app_user (id, first_name, person_code, active) VALUES (:id, 'Ana', 'P1', true)")
                .param("id", user).update();
        for (String hash : new String[] {"h1", "h2"}) {
            jdbc.sql("INSERT INTO admin_session (token_hash, user_id, created_at, last_seen_at) "
                    + "VALUES (:h, :u, :c, :c)").param("h", hash).param("u", user)
                    .param("c", Timestamp.from(created)).update();
        }

        Flyway.configure().dataSource(database).placeholders(placeholders).load().migrate();

        var rows = jdbc.sql("SELECT id, user_agent, ip, reauthenticated_at, created_at FROM admin_session")
                .query((rs, n) -> Map.of("id", rs.getObject("id", UUID.class),
                        "ua", String.valueOf(rs.getString("user_agent")), "ip", String.valueOf(rs.getString("ip")),
                        "same", rs.getTimestamp("reauthenticated_at").equals(rs.getTimestamp("created_at"))))
                .list();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(r -> r.get("id")).doesNotHaveDuplicates().doesNotContainNull();
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.get("ua")).isEqualTo("null");
            assertThat(r.get("ip")).isEqualTo("null");
            assertThat(r.get("same")).isEqualTo(true);
        });
    }
}
