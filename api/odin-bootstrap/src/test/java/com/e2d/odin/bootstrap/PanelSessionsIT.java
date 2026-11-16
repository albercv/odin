package com.e2d.odin.bootstrap;

import static com.e2d.odin.bootstrap.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.bootstrap.ApiClient.Reply;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

/** Caducidad, lista propia y cierre de las sesiones del panel, por HTTP y con filas reales. */
@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class PanelSessionsIT {

    private static final String SETUP_TOKEN = "setup-panel-sessions-token";
    private static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");
    private static final String PASSWORD = "clave-muy-larga-1";
    private static final String ADMIN = "/v1/admin";
    private static final String BROWSER = "Mozilla/5.0 (pruebas)";

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
        registry.add("ODIN_SETUP_TOKEN", () -> SETUP_TOKEN);
        registry.add("ODIN_ORIGINALS_DIR", () -> System.getProperty("java.io.tmpdir"));
        registry.add("ODIN_UPLOAD_MAX_BYTES", () -> "1000000");
        registry.add("ODIN_PUBLIC_URL", () -> "http://odin.test/");
        registry.add("ODIN_TRUSTED_PROXIES", () -> "127.0.0.1");
    }

    @LocalServerPort int port;

    private static boolean installed;
    private ApiClient api;
    private JdbcClient owner;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        owner = JdbcClient.create(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        if (!installed) {
            assertThat(api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo", "adminName",
                    "Admin", "adminEmail", "admin@ejemplo.test", "password", PASSWORD)).status()).isEqualTo(201);
            String admin = login("admin@ejemplo.test");
            for (String[] c : new String[][] {{"Alfa", "ALFA01", "carla@ejemplo.test"},
                    {"Beta", "BETA01", "dora@ejemplo.test"}}) {
                String id = call(admin, "post", ADMIN + "/companies", map("name", c[0], "code", c[1])).json()
                        .get("id").asString();
                call(admin, "post", ADMIN + "/companies/" + id + "/members", map("firstName", c[0], "lastName", "Admin",
                        "email", c[2], "roles", List.of("COMPANY_ADMIN"), "password", PASSWORD));
            }
            installed = true;
        }
        owner.sql("DELETE FROM admin_session").update();
        owner.sql("UPDATE audit_setting SET enabled = true, usage_policy_reference = 'https://intranet.ejemplo.test/p'")
                .update();
        owner.sql("ALTER TABLE audit_event DISABLE TRIGGER USER").update();
        owner.sql("TRUNCATE audit_event, audit_actor").update();
        owner.sql("ALTER TABLE audit_event ENABLE TRIGGER USER").update();
    }

    private String login(String email) {
        return api.post(ADMIN + "/session", map("email", email, "password", PASSWORD), "User-Agent", BROWSER,
                "X-Forwarded-For", "203.0.113.9").setCookie().split(";")[0];
    }

    private Reply call(String cookie, String method, String path, Object body) {
        return "get".equals(method) ? api.get(path, "Cookie", cookie) : "delete".equals(method)
                ? api.delete(path, "Cookie", cookie) : api.post(path, body, "Cookie", cookie);
    }

    private Reply get(String cookie, String path) {
        return call(cookie, "get", path, null);
    }

    private JsonNode sessionsOf(String cookie) {
        return get(cookie, ADMIN + "/sessions").json();
    }

    private List<Map<String, Object>> entries(String action) {
        return owner.sql("SELECT * FROM audit_event WHERE action = :a").param("a", action).query().listOfRows();
    }

    @Test
    void loginStoresBrowserAndIpAndListMarksTheCurrentSession() {
        String first = login("carla@ejemplo.test");
        String second = login("carla@ejemplo.test");

        JsonNode list = sessionsOf(second);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("userAgent").asString()).isEqualTo(BROWSER);
        assertThat(list.get(0).get("ip").asString()).isEqualTo("203.0.113.9");
        assertThat(list.get(0).get("current").asBoolean()).isTrue();
        assertThat(list.get(1).get("current").asBoolean()).isFalse();
        assertThat(list.toString()).doesNotContain("tokenHash").doesNotContain("token_hash");
        assertThat(sessionsOf(first).get(1).get("current").asBoolean()).isTrue();
    }

    @Test
    void listShowsOnlyTheCallersSessionsAcrossAdminsAndCompanies() {
        String carla = login("carla@ejemplo.test");
        String dora = login("dora@ejemplo.test");
        String group = login("admin@ejemplo.test");

        assertThat(sessionsOf(carla)).hasSize(1);
        assertThat(sessionsOf(dora)).hasSize(1);
        assertThat(sessionsOf(group)).hasSize(1);
        assertThat(sessionsOf(carla).get(0).get("id").asString())
                .isNotEqualTo(sessionsOf(dora).get(0).get("id").asString());
    }

    @Test
    void closingAForeignSessionIs404AndItStaysOpenButOwnClosesWithAnEntry() {
        String carla = login("carla@ejemplo.test");
        String carlaOther = login("carla@ejemplo.test");
        String dora = login("dora@ejemplo.test");
        String doraId = sessionsOf(dora).get(0).get("id").asString();
        String otherId = sessionsOf(carla).get(0).get("id").asString();

        assertThat(call(carla, "delete", ADMIN + "/sessions/" + doraId, null).status()).isEqualTo(404);
        assertThat(call(carla, "delete", ADMIN + "/sessions/" + java.util.UUID.randomUUID(), null).status())
                .isEqualTo(404);
        assertThat(get(dora, ADMIN + "/me").status()).isEqualTo(200);
        assertThat(entries("SESSION_CLOSED")).isEmpty();

        assertThat(call(carla, "delete", ADMIN + "/sessions/" + otherId, null).status()).isEqualTo(204);

        assertThat(get(carlaOther, ADMIN + "/me").status()).as("la cerrada o la otra").isIn(200, 401);
        assertThat(entries("SESSION_CLOSED")).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("channel", "PANEL").containsEntry("target_kind", "SESSION")
                    .containsEntry("target_id", otherId);
            assertThat(row.get("details").toString()).doesNotContain("ip").doesNotContain("Mozilla");
        });
    }

    @Test
    void closingTheCurrentSessionWorksAndTheNextRequestIs401() {
        String carla = login("carla@ejemplo.test");
        String id = sessionsOf(carla).get(0).get("id").asString();

        assertThat(call(carla, "delete", ADMIN + "/sessions/" + id, null).status()).isEqualTo(204);

        assertThat(get(carla, ADMIN + "/me").status()).isEqualTo(401);
    }

    @Test
    void meReportsTheCloserOfIdleAndAbsoluteExpiry() {
        String carla = login("carla@ejemplo.test");
        Instant now = Instant.now();

        Instant idle = Instant.parse(get(carla, ADMIN + "/me").json().get("expiresAt").asString());
        assertThat(idle).isBetween(now.plusSeconds(29 * 60), now.plusSeconds(31 * 60));

        owner.sql("UPDATE admin_session SET created_at = now() - interval '11 hours 50 minutes'").update();
        Instant absolute = Instant.parse(get(carla, ADMIN + "/me").json().get("expiresAt").asString());
        assertThat(absolute).isBetween(now.plusSeconds(9 * 60), now.plusSeconds(11 * 60));
    }

    @Test
    void idleSessionIs401AndLeavesSessionExpiredWithItsUserAndNoSlide() {
        String carla = login("carla@ejemplo.test");
        owner.sql("UPDATE admin_session SET last_seen_at = now() - interval '31 minutes'").update();

        assertThat(get(carla, ADMIN + "/me").status()).isEqualTo(401);

        assertThat(owner.sql("SELECT count(*) FROM admin_session").query(Integer.class).single()).isZero();
        assertThat(entries("SESSION_EXPIRED")).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("channel", "PANEL").containsEntry("outcome", "DONE")
                    .containsEntry("actor_role", "COMPANY_ADMIN");
            assertThat(row.get("actor_user_id")).isEqualTo(owner.sql(
                    "SELECT id FROM app_user WHERE email = 'carla@ejemplo.test'").query(java.util.UUID.class).single());
            assertThat(row.get("details").toString()).contains("IDLE");
        });
        assertThat(get(carla, ADMIN + "/me").status()).isEqualTo(401);
        assertThat(entries("SESSION_EXPIRED")).hasSize(1);
    }

    @Test
    void absoluteLimitExpiresASessionInConstantUse() {
        String carla = login("carla@ejemplo.test");
        assertThat(get(carla, ADMIN + "/me").status()).isEqualTo(200);
        owner.sql("UPDATE admin_session SET created_at = now() - interval '12 hours 1 minute'").update();

        assertThat(get(carla, ADMIN + "/me").status()).isEqualTo(401);

        assertThat(entries("SESSION_EXPIRED")).singleElement()
                .satisfies(row -> assertThat(row.get("details").toString()).contains("ABSOLUTE"));
    }
}
