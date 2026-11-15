package com.e2d.odin.bootstrap;

import static com.e2d.odin.bootstrap.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Sin proxies de confianza, cambiar X-Forwarded-For en cada intento no reinicia el límite por IP. */
@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class ForwardedForRateLimitIT {

    private static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");

    static {
        PG.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("ODIN_DB_URL", PG::getJdbcUrl);
        registry.add("ODIN_DB_OWNER_USER", PG::getUsername);
        registry.add("ODIN_DB_OWNER_PASSWORD", PG::getPassword);
        registry.add("ODIN_DB_APP_USER", () -> "odin_app");
        registry.add("ODIN_DB_APP_PASSWORD", () -> "app-test-password");
        registry.add("ODIN_SETUP_TOKEN", () -> "setup-limit-token");
        registry.add("ODIN_ORIGINALS_DIR", () -> System.getProperty("java.io.tmpdir"));
        registry.add("ODIN_UPLOAD_MAX_BYTES", () -> "1000000");
    }

    @LocalServerPort int port;

    @Test
    void rotatingForwardedForDoesNotResetTheLoginLimit() {
        ApiClient api = new ApiClient("127.0.0.1", port);
        int last = 0;
        for (int i = 0; i < 7; i++) {
            last = api.post("/v1/admin/session", map("email", "nadie" + i + "@ejemplo.test", "password", "x"),
                    "X-Forwarded-For", "192.168.7." + i).status();
        }

        assertThat(last).isEqualTo(429);
    }
}
