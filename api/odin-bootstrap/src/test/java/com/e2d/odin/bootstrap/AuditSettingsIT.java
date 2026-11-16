package com.e2d.odin.bootstrap;

import static com.e2d.odin.bootstrap.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.bootstrap.ApiClient.Reply;
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
import tools.jackson.databind.json.JsonMapper;

/** El interruptor del registro: quién lo toca, con qué contraseña y qué deja escrito. */
@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class AuditSettingsIT {

    private static final String SETUP_TOKEN = "setup-audit-token";
    private static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");
    private static final String ADMIN_EMAIL = "admin@ejemplo.test";
    private static final String PASSWORD = "clave-muy-larga-1";
    private static final String URL = "/v1/admin/audit-settings";
    private static final String POLICY = "https://intranet.ejemplo.test/politica-de-uso";

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
    }

    @LocalServerPort int port;

    private static String adminCookie;
    private static String companyAdminCookie;
    private ApiClient api;
    private JdbcClient owner;

    @BeforeEach
    void setUpOnce() {
        api = new ApiClient(port);
        owner = JdbcClient.create(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        if (adminCookie == null) {
            installAndLogIn();
        }
        owner.sql("ALTER TABLE audit_event DISABLE TRIGGER USER").update();
        owner.sql("TRUNCATE audit_event, audit_actor").update();
        owner.sql("ALTER TABLE audit_event ENABLE TRIGGER USER").update();
        owner.sql("UPDATE audit_setting SET enabled = false, usage_policy_reference = NULL, chat_enabled = false")
                .update();
    }

    private void installAndLogIn() {
        assertThat(api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo Ejemplo", "adminName",
                "Admin", "adminEmail", ADMIN_EMAIL, "password", PASSWORD)).status()).isEqualTo(201);
        adminCookie = api.post("/v1/admin/session", map("email", ADMIN_EMAIL, "password", PASSWORD)).setCookie()
                .split(";")[0];
        String company = api.post("/v1/admin/companies", map("name", "Alfa", "code", "ALFA01"), "Cookie", adminCookie)
                .json().get("id").asString();
        assertThat(api.post("/v1/admin/companies/" + company + "/members", map("firstName", "Carla", "lastName",
                "Admin", "email", "carla@ejemplo.test", "roles", List.of("COMPANY_ADMIN"), "password", PASSWORD),
                "Cookie", adminCookie).status()).isEqualTo(201);
        companyAdminCookie = api.post("/v1/admin/session", map("email", "carla@ejemplo.test", "password", PASSWORD))
                .setCookie().split(";")[0];
    }

    private Reply put(Object body) {
        return api.put(URL, body, "Cookie", adminCookie);
    }

    private static Map<String, Object> body(boolean enabled, String reference, String password) {
        return map("enabled", enabled, "usagePolicyReference", reference, "chatEnabled", false, "password", password);
    }

    private List<Map<String, Object>> entries() {
        return owner.sql("SELECT * FROM audit_event ORDER BY id").query().listOfRows();
    }

    @Test
    void groupAdminReadsTheDefaultSwitch() {
        Reply reply = api.get(URL, "Cookie", adminCookie);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().get("enabled").asBoolean()).isFalse();
        assertThat(reply.json().get("chatEnabled").asBoolean()).isFalse();
    }

    @Test
    void companyAdminAndAnonymousCannotTouchIt() {
        assertThat(api.get(URL, "Cookie", companyAdminCookie).status()).isEqualTo(403);
        assertThat(api.put(URL, body(true, POLICY, PASSWORD), "Cookie", companyAdminCookie).status()).isEqualTo(403);
        assertThat(api.get(URL).status()).isEqualTo(401);
        assertThat(entries()).isEmpty();
    }

    @Test
    void enablingWithoutPolicyReferenceIsBadRequest() {
        assertThat(put(body(true, "  ", PASSWORD)).status()).isEqualTo(400);
        assertThat(put(body(true, null, PASSWORD)).status()).isEqualTo(400);
    }

    @Test
    void omittingEnabledOrChatEnabledIsBadRequestAndChangesNothing() {
        Map<String, Object> on = map("enabled", true, "usagePolicyReference", POLICY, "chatEnabled", true, "password",
                PASSWORD);
        assertThat(put(on).status()).isEqualTo(200);
        for (String missing : List.of("enabled", "chatEnabled")) {
            Map<String, Object> partial = new java.util.HashMap<>(on);
            partial.remove(missing);
            Reply reply = put(partial);
            assertThat(reply.status()).as(missing).isEqualTo(400);
            assertThat(reply.json().get("detail").asString()).contains("enabled".equals(missing) ? "registro de actividad está" : "chat puede");
            JsonNode now = api.get(URL, "Cookie", adminCookie).json();
            assertThat(now.get("enabled").asBoolean()).isTrue();
            assertThat(now.get("chatEnabled").asBoolean()).isTrue();
        }
    }

    @Test
    void enablingLeavesTheFirstEntryAndDisablingTheLast() {
        assertThat(put(body(true, POLICY, PASSWORD)).status()).isEqualTo(200);
        assertThat(put(body(false, null, PASSWORD)).status()).isEqualTo(200);

        List<Map<String, Object>> rows = entries();
        assertThat(rows).extracting(r -> r.get("action")).containsExactly("AUDIT_ENABLED", "AUDIT_DISABLED");
        assertThat(rows).extracting(r -> r.get("channel")).containsOnly("PANEL");
        assertThat(rows).extracting(r -> r.get("request_id")).doesNotContainNull();
        assertThat(owner.sql("SELECT audit_verify()").query((rs, n) -> (Long) rs.getObject(1)).list().get(0)).isNull();
        rows.forEach(r -> AuditAssertions.assertNoForbiddenFields(
                JsonMapper.builder().build().readTree(r.get("details").toString())));
        assertThat(api.get(URL, "Cookie", adminCookie).json().get("enabled").asBoolean()).isFalse();
    }

    @Test
    void wrongPasswordIsForbiddenAndLeavesADeniedEntry() {
        assertThat(put(body(true, POLICY, PASSWORD)).status()).isEqualTo(200);

        assertThat(put(body(false, null, "no-es-esta-clave")).status()).isEqualTo(403);

        List<Map<String, Object>> rows = entries();
        assertThat(rows).extracting(r -> r.get("outcome")).containsExactly("DONE", "DENIED");
        assertThat(rows.get(1)).containsEntry("action", "REAUTHENTICATION_FAILED");
        assertThat(api.get(URL, "Cookie", adminCookie).json().get("enabled").asBoolean()).isTrue();
    }
}
