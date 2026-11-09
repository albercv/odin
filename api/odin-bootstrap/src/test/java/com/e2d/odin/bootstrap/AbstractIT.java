package com.e2d.odin.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
abstract class AbstractIT {

    static final String APP_PASSWORD = "app-test-password";
    static final long MAX_UPLOAD = 1_000_000;
    static final String PANEL = "http://panel.test";
    static final String SETUP_TOKEN = "setup-test-token";
    private static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");

    static {
        PG.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        registry.add("ODIN_DB_URL", PG::getJdbcUrl);
        registry.add("ODIN_DB_OWNER_USER", PG::getUsername);
        registry.add("ODIN_DB_OWNER_PASSWORD", PG::getPassword);
        registry.add("ODIN_DB_APP_USER", () -> "odin_app");
        registry.add("ODIN_DB_APP_PASSWORD", () -> APP_PASSWORD);
        registry.add("ODIN_SETUP_TOKEN", () -> SETUP_TOKEN);
        registry.add("ODIN_PANEL_ORIGIN", () -> PANEL);
        registry.add("ODIN_ORIGINALS_DIR", () -> tempDir());
        registry.add("ODIN_UPLOAD_MAX_BYTES", () -> String.valueOf(MAX_UPLOAD));
    }

    private static String tempDir() {
        try {
            return Files.createTempDirectory("odin-originals").toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
