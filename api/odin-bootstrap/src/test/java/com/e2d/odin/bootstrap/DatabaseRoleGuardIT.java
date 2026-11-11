package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class DatabaseRoleGuardIT {

    private static final String APP_PASSWORD = "app-test-password";

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");

    @BeforeAll
    static void migrateAndCreateRoles() {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .placeholders(Map.of("appPassword", APP_PASSWORD)).load().migrate();
        JdbcClient owner = JdbcClient.create(dataSource(PG.getUsername(), PG.getPassword()));
        owner.sql("CREATE ROLE rls_bypasser LOGIN NOSUPERUSER BYPASSRLS PASSWORD 'x'").update();
        owner.sql("CREATE ROLE owner_member LOGIN NOSUPERUSER PASSWORD 'x' IN ROLE " + PG.getUsername()).update();
    }

    private static DataSource dataSource(String user, String password) {
        return new DriverManagerDataSource(PG.getJdbcUrl(), user, password);
    }

    @Test
    void applicationDoesNotStartWhenConnectedAsTheOwnerRole() throws Exception {
        SpringApplicationBuilder app = new SpringApplicationBuilder(OdinApplication.class).properties(
                "server.port=0", "ODIN_DB_URL=" + PG.getJdbcUrl(), "ODIN_DB_OWNER_USER=" + PG.getUsername(),
                "ODIN_DB_OWNER_PASSWORD=" + PG.getPassword(), "ODIN_DB_APP_USER=" + PG.getUsername(),
                "ODIN_DB_APP_PASSWORD=" + PG.getPassword(),
                "ODIN_ORIGINALS_DIR=" + Files.createTempDirectory("odin-originals"));

        assertThatThrownBy(() -> app.run().close()).rootCause().isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("«" + PG.getUsername() + "»").hasMessageContaining("es superusuario")
                .hasMessageContaining("es dueño de la tabla document").hasMessageContaining("odin_app");
    }

    @Test
    void rejectsARoleWithBypassRls() {
        assertThatThrownBy(() -> DatabaseRoleGuard.check(dataSource("rls_bypasser", "x")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tiene BYPASSRLS");
    }

    @Test
    void rejectsAMemberOfTheOwnerRole() {
        assertThatThrownBy(() -> DatabaseRoleGuard.check(dataSource("owner_member", "x")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("es dueño de la tabla document")
                .message().doesNotContain("superusuario");
    }

    @Test
    void acceptsTheApplicationRole() {
        assertThatCode(() -> DatabaseRoleGuard.check(dataSource("odin_app", APP_PASSWORD)))
                .doesNotThrowAnyException();
    }
}
