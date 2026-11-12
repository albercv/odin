package com.e2d.odin.adapter.postgres;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

final class Db {

    static final String APP_PASSWORD = "app-test-password";
    private static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");
    private static final DataSource OWNER;
    private static final DataSource APP;

    static {
        PG.start();
        OWNER = dataSource(PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(OWNER).placeholders(Map.of("appPassword", APP_PASSWORD)).load().migrate();
        APP = dataSource("odin_app", APP_PASSWORD);
    }

    private Db() {
    }

    static DataSource owner() {
        return OWNER;
    }

    static DataSource app() {
        return APP;
    }

    /** Base de datos nueva y vacía en el mismo contenedor, para pruebas que migran paso a paso. */
    static DataSource freshDatabase(String name) {
        try (Connection c = OWNER.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
            s.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return new DriverManagerDataSource(PG.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1"),
                PG.getUsername(), PG.getPassword());
    }

    static void truncate() {
        try (Connection c = OWNER.getConnection(); Statement s = c.createStatement()) {
            s.execute("TRUNCATE question_record, knowledge_card, document_page, document, admin_session, access_key, "
                    + "membership, app_user, project, company_capability, company, odin_group CASCADE");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DataSource dataSource(String user, String password) {
        return new DriverManagerDataSource(PG.getJdbcUrl(), user, password);
    }
}
