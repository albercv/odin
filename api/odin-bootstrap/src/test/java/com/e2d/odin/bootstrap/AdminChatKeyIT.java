package com.e2d.odin.bootstrap;

import static com.e2d.odin.bootstrap.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.bootstrap.ApiClient.Reply;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** La clave de administrador para el chat por HTTP, contra una base real y con dos empresas. */
@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class AdminChatKeyIT {

    private static final String SETUP_TOKEN = "setup-chat-key-token";
    private static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");
    private static final String PASSWORD = "clave-muy-larga-1";
    private static final String KEYS = "/v1/admin/activity-keys";
    private static final String RANGE = "from=2026-01-01T00:00:00Z&to=2026-01-05T00:00:00Z";

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

    private static String groupCookie;
    private static String alfaCookie;
    private static String betaCookie;
    private static String workerBearer;
    private static UUID alfa;
    private static UUID beta;
    private static UUID carla;
    private ApiClient api;
    private JdbcClient owner;

    @BeforeEach
    void setUpOnce() {
        api = new ApiClient(port);
        owner = JdbcClient.create(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        if (groupCookie == null) {
            install();
        }
        owner.sql("ALTER TABLE audit_event DISABLE TRIGGER USER").update();
        owner.sql("TRUNCATE audit_event, audit_actor").update();
        owner.sql("ALTER TABLE audit_event ENABLE TRIGGER USER").update();
        owner.sql("DELETE FROM admin_chat_key").update();
        owner.sql("UPDATE membership SET is_company_admin = true, is_worker = false WHERE user_id = :u")
                .param("u", carla).update();
        owner.sql("UPDATE audit_setting SET enabled = true, chat_enabled = true, "
                + "usage_policy_reference = 'https://politica.test'").update();
    }

    private void install() {
        api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo", "adminName", "Admin",
                "adminEmail", "admin@ejemplo.test", "password", PASSWORD));
        groupCookie = login("admin@ejemplo.test");
        alfa = UUID.fromString(company("Alfa", "ALFA01"));
        beta = UUID.fromString(company("Beta", "BETA01"));
        carla = member(alfa, "carla@ejemplo.test", "COMPANY_ADMIN");
        alfaCookie = login("carla@ejemplo.test");
        member(beta, "dani@ejemplo.test", "COMPANY_ADMIN");
        betaCookie = login("dani@ejemplo.test");
        UUID worker = member(alfa, "marta@ejemplo.test", "WORKER");
        workerBearer = "Bearer " + api.post("/v1/admin/companies/" + alfa + "/members/" + worker + "/keys",
                map("label", "portatil"), "Cookie", groupCookie).json().get("key").asString();
    }

    private String company(String name, String code) {
        return api.post("/v1/admin/companies", map("name", name, "code", code), "Cookie", groupCookie).json()
                .get("id").asString();
    }

    private UUID member(UUID company, String email, String role) {
        Map<String, Object> body = role.equals("WORKER")
                ? map("firstName", "Ana", "lastName", "Persona", "email", email, "roles", List.of(role))
                : map("firstName", "Ana", "lastName", "Persona", "email", email, "roles", List.of(role),
                        "password", PASSWORD);
        return UUID.fromString(api.post("/v1/admin/companies/" + company + "/members", body, "Cookie", groupCookie)
                .json().get("member").get("userId").asString());
    }

    private String login(String email) {
        return api.post("/v1/admin/session", map("email", email, "password", PASSWORD)).setCookie().split(";")[0];
    }

    private Reply issue(String cookie, String password, UUID company) {
        Map<String, Object> body = company == null ? map("password", password)
                : map("password", password, "companyId", company.toString());
        return api.post(KEYS, body, "Cookie", cookie);
    }

    private String token(String cookie, UUID company) {
        Reply reply = issue(cookie, PASSWORD, company);
        assertThat(reply.status()).as(reply.body()).isEqualTo(201);
        return reply.json().get("token").asString();
    }

    private Reply activity(String token, String query) {
        return api.get("/v1/activity?" + query, "Authorization", "Bearer " + token);
    }

    private long seed(String at, UUID company, String action, String details) {
        return seed(at, company, action, details, "SYSTEM");
    }

    private long seed(String at, UUID company, String action, String details, String channel) {
        return owner.sql("""
                INSERT INTO audit_event (occurred_at, company_id, actor_kind, channel, action, outcome, details,
                    chain_key, prev_hash, hash)
                VALUES (CAST(:at AS timestamptz), :company, 'SYSTEM', :channel, :action, 'DONE',
                    CAST(:details AS jsonb), 'main', :prev, :hash) RETURNING id""")
                .param("at", at).param("company", company, java.sql.Types.OTHER).param("action", action)
                .param("channel", channel)
                .param("details", details).param("prev", UUID.randomUUID().toString())
                .param("hash", UUID.randomUUID().toString()).query(Long.class).single();
    }

    private List<Map<String, Object>> entries(String action) {
        return owner.sql("SELECT channel, outcome, company_id, CAST(details AS text) AS details FROM audit_event "
                + "WHERE action = :a ORDER BY id").param("a", action).query().listOfRows();
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("items").forEach(row -> ids.add(row.get("id").asLong()));
        return ids;
    }

    @Test
    void groupAdminGetsGroupScopeKeyAndCompanyIsRejected() {
        Reply reply = issue(groupCookie, PASSWORD, null);

        assertThat(reply.status()).isEqualTo(201);
        JsonNode key = reply.json();
        assertThat(key.get("token").asString()).startsWith("odin-adm_").contains("_" + "");
        assertThat(key.get("token").asString()).contains(key.get("prefix").asString());
        assertThat(Instant.parse(key.get("expiresAt").asString()))
                .isBetween(Instant.now().plus(29, ChronoUnit.DAYS), Instant.now().plus(31, ChronoUnit.DAYS));
        assertThat(issue(groupCookie, PASSWORD, alfa).status()).isEqualTo(400);
        String stored = owner.sql("SELECT token_hash FROM admin_chat_key").query(String.class).single();
        assertThat(key.get("token").asString()).doesNotContain(stored);
        JsonNode issued = new tools.jackson.databind.json.JsonMapper().readTree(
                entries("ADMIN_CHAT_KEY_ISSUED").get(0).get("details").toString());
        assertThat(issued.get("prefix").asString()).isEqualTo(key.get("prefix").asString());
        assertThat(entries("ADMIN_CHAT_KEY_ISSUED").get(0).get("details").toString())
                .doesNotContain(key.get("token").asString());
        AuditAssertions.assertNoForbiddenFields(issued);
    }

    @Test
    void companyAdminIssuesForOwnCompanyAndNotForAnother() {
        assertThat(issue(alfaCookie, PASSWORD, beta).status()).isEqualTo(403);
        assertThat(entries("ADMIN_CHAT_KEY_ISSUED")).extracting(e -> e.get("outcome")).containsExactly("DENIED");

        Reply defaulted = issue(alfaCookie, PASSWORD, null);
        assertThat(defaulted.status()).as(defaulted.body()).isEqualTo(201);
        assertThat(issue(alfaCookie, PASSWORD, alfa).status()).isEqualTo(201);
        assertThat(owner.sql("SELECT company_id FROM admin_chat_key WHERE revoked_at IS NULL")
                .query(UUID.class).single()).isEqualTo(alfa);
    }

    @Test
    void wrongPasswordIs403WithDeniedEntryAndChatDisabledIs409() {
        assertThat(issue(groupCookie, "otra-clave-larga-1", null).status()).isEqualTo(403);
        assertThat(entries("REAUTHENTICATION_FAILED")).extracting(e -> e.get("outcome")).containsExactly("DENIED");
        assertThat(owner.sql("SELECT count(*) FROM admin_chat_key").query(Long.class).single()).isZero();

        owner.sql("UPDATE audit_setting SET chat_enabled = false").update();
        Reply off = issue(groupCookie, PASSWORD, null);
        assertThat(off.status()).isEqualTo(409);
        assertThat(off.body()).contains("activados");
        owner.sql("UPDATE audit_setting SET chat_enabled = true, enabled = false").update();
        assertThat(issue(groupCookie, PASSWORD, null).status()).isEqualTo(409);
    }

    @Test
    void issuingRevokesThePreviousActiveKey() {
        String first = token(groupCookie, null);
        String second = token(groupCookie, null);

        assertThat(activity(first, RANGE).status()).isEqualTo(401);
        assertThat(activity(second, RANGE).status()).isEqualTo(200);
        assertThat(entries("ADMIN_CHAT_KEY_REVOKED")).hasSize(1);
        assertThat(api.get(KEYS, "Cookie", groupCookie).json().get("key").get("prefix").asString())
                .isEqualTo(second.substring("odin-adm_".length(), "odin-adm_".length() + 8));
    }

    @Test
    void listShowsOnlyMetadataAndRevokeRules() {
        assertThat(api.get(KEYS, "Cookie", alfaCookie).json().get("key").isNull()).isTrue();
        assertThat(api.get(KEYS, "Cookie", alfaCookie).json().get("chatAvailable").asBoolean()).isTrue();
        Reply issued = issue(alfaCookie, PASSWORD, null);
        String id = issued.json().get("id").asString();

        Reply listed = api.get(KEYS, "Cookie", alfaCookie);
        assertThat(listed.json().get("key").get("id").asString()).isEqualTo(id);
        assertThat(listed.body()).doesNotContain("token").doesNotContain(issued.json().get("token").asString());
        assertThat(api.get(KEYS, "Cookie", betaCookie).json().get("key").isNull()).isTrue();

        assertThat(api.delete(KEYS + "/" + id, "Cookie", betaCookie).status()).isEqualTo(403);
        assertThat(api.delete(KEYS + "/" + UUID.randomUUID(), "Cookie", betaCookie).status()).isEqualTo(404);
        assertThat(api.delete(KEYS + "/" + id, "Authorization", workerBearer).status()).isEqualTo(401);
        assertThat(api.delete(KEYS + "/" + id, "Cookie", groupCookie).status()).isEqualTo(204);
        assertThat(api.get(KEYS, "Cookie", alfaCookie).json().get("key").isNull()).isTrue();
        assertThat(activity(issued.json().get("token").asString(), RANGE).status()).isEqualTo(401);

        String own = issue(alfaCookie, PASSWORD, null).json().get("id").asString();
        assertThat(api.delete(KEYS + "/" + own, "Cookie", alfaCookie).status()).isEqualTo(204);
        assertThat(entries("ADMIN_CHAT_KEY_REVOKED")).hasSize(2);
    }

    @Test
    void workerKeyOnActivityIs403AndChatKeyElsewhereIsRejected() {
        String token = token(groupCookie, null);
        String bearer = "Bearer " + token;

        assertThat(api.get("/v1/activity?" + RANGE, "Authorization", workerBearer).status()).isEqualTo(403);
        assertThat(api.get("/v1/activity?" + RANGE).status()).isEqualTo(401);
        assertThat(api.get("/v1/documents", "Authorization", bearer).status()).isEqualTo(401);
        assertThat(api.get("/v1/me/cards", "Authorization", bearer).status()).isEqualTo(401);
        assertThat(api.get("/v1/admin/me", "Authorization", bearer).status()).isEqualTo(401);
        assertThat(api.get("/v1/admin/activity?" + RANGE, "Authorization", bearer).status()).isEqualTo(401);
        assertThat(api.get(KEYS, "Authorization", bearer).status()).isEqualTo(401);
        assertThat(api.get("/v1/admin/me", "Authorization", workerBearer).status()).isEqualTo(401);
    }

    @Test
    void expiredRevokedAndLostRoleKeys() {
        String expired = token(alfaCookie, null);
        owner.sql("UPDATE admin_chat_key SET expires_at = now() - interval '1 minute'").update();
        assertThat(activity(expired, RANGE).status()).isEqualTo(401);

        String revoked = token(alfaCookie, null);
        api.delete(KEYS + "/" + api.get(KEYS, "Cookie", alfaCookie).json().get("key").get("id").asString(),
                "Cookie", alfaCookie);
        assertThat(activity(revoked, RANGE).status()).isEqualTo(401);

        String demoted = token(alfaCookie, null);
        assertThat(activity(demoted, RANGE).status()).isEqualTo(200);
        owner.sql("UPDATE membership SET is_company_admin = false, is_worker = true WHERE user_id = :u")
                .param("u", carla).update();
        assertThat(activity(demoted, RANGE).status()).isEqualTo(403);
        assertThat(api.get("/v1/me", "Authorization", "Bearer " + demoted).status()).isEqualTo(403);

        assertThat(activity("odin-adm_AAAAAAAA_nada", RANGE).status()).isEqualTo(401);
        assertThat(activity("odin-adm_corto", RANGE).status()).isEqualTo(401);
    }

    @Test
    void groupAdminRevokedAsGroupAdminLosesTheKey() {
        String other = api.post("/v1/admin/group-admins", map("firstName", "Otra", "lastName", "Admin", "email",
                "otra@ejemplo.test", "password", PASSWORD), "Cookie", groupCookie).json().get("admin")
                .get("userId").asString();
        String token = token(login("otra@ejemplo.test"), null);
        assertThat(activity(token, RANGE).status()).isEqualTo(200);

        api.delete("/v1/admin/group-admins/" + other, "Cookie", groupCookie);

        assertThat(activity(token, RANGE).status()).isEqualTo(403);
    }

    @Test
    void switchesOffMeanForbidden() {
        String token = token(groupCookie, null);
        owner.sql("UPDATE audit_setting SET chat_enabled = false").update();
        assertThat(activity(token, RANGE).status()).isEqualTo(403);
        assertThat(api.get("/v1/me", "Authorization", "Bearer " + token).status()).isEqualTo(403);
        owner.sql("UPDATE audit_setting SET chat_enabled = true, enabled = false").update();
        assertThat(activity(token, RANGE).status()).isEqualTo(403);
    }

    @Test
    void settingsToggleLeavesItsOwnEntry() {
        owner.sql("UPDATE audit_setting SET chat_enabled = false").update();
        Reply reply = api.put("/v1/admin/audit-settings", map("enabled", true, "usagePolicyReference",
                "https://politica.test", "chatEnabled", true, "password", PASSWORD), "Cookie", groupCookie);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(entries("CHAT_ENABLED")).hasSize(1);
    }

    @Test
    void rangeIsMandatoryAndAtMostSevenDays() {
        String token = token(groupCookie, null);

        assertThat(activity(token, "").status()).isEqualTo(400);
        assertThat(activity(token, "from=2026-01-01T00:00:00Z").status()).isEqualTo(400);
        Reply tooLong = activity(token, "from=2026-01-01T00:00:00Z&to=2026-01-08T00:00:01Z");
        assertThat(tooLong.status()).isEqualTo(400);
        assertThat(tooLong.body()).contains("7 días");
        assertThat(activity(token, "from=2026-01-05T00:00:00Z&to=2026-01-01T00:00:00Z").status()).isEqualTo(400);
        assertThat(activity(token, "from=2026-01-01T00:00:00Z&to=2026-01-08T00:00:00Z").status()).isEqualTo(200);
    }

    @Test
    void companyScopeKeySeesOnlyItsCompanyAndNeverGroupRows() {
        long a = seed("2026-01-02T10:00:00Z", alfa, "COMPANY_UPDATED", "{}");
        seed("2026-01-02T10:01:00Z", beta, "COMPANY_UPDATED", "{}");
        seed("2026-01-02T10:02:00Z", null, "COMPANY_UPDATED", "{}");
        String token = token(alfaCookie, null);

        JsonNode page = activity(token, RANGE).json();
        assertThat(ids(page)).containsExactly(a);
        assertThat(activity(token, RANGE + "&companyId=" + alfa).json().get("items")).hasSize(1);

        Reply foreign = activity(token, RANGE + "&companyId=" + beta);
        assertThat(foreign.status()).isEqualTo(403);
        assertThat(entries("CHAT_QUERY")).filteredOn(e -> e.get("outcome").equals("DENIED")).hasSize(1);
        assertThat(entries("CHAT_QUERY").getLast().get("details").toString()).contains("OUT_OF_SCOPE");
    }

    @Test
    void groupScopeKeySeesEverythingIncludingGroupRows() {
        long a = seed("2026-01-02T10:00:00Z", alfa, "COMPANY_UPDATED", "{}");
        long b = seed("2026-01-02T10:01:00Z", beta, "COMPANY_UPDATED", "{}");
        long g = seed("2026-01-02T10:02:00Z", null, "COMPANY_UPDATED", "{}");
        String token = token(groupCookie, null);

        assertThat(ids(activity(token, RANGE + "&action=COMPANY_UPDATED").json())).containsExactly(g, b, a);
        assertThat(ids(activity(token, RANGE + "&action=COMPANY_UPDATED&companyId=" + beta).json()))
                .containsExactly(b);
    }

    @Test
    void outputNeverCarriesEmailIpOrUserAgent() {
        seed("2026-01-02T10:00:00Z", alfa, "LOGIN_SUCCEEDED",
                "{\"ip\":\"203.0.113.9\",\"userAgent\":\"Mozilla/5.0\",\"email\":\"x@y.test\","
                        + "\"nested\":{\"ip\":\"198.51.100.1\"},\"kept\":\"si\"}");
        String token = token(groupCookie, null);

        Reply reply = activity(token, RANGE + "&action=LOGIN_SUCCEEDED");

        assertThat(reply.json().get("items")).hasSize(1);
        assertThat(reply.body()).doesNotContain("203.0.113.9", "Mozilla", "198.51.100.1", "x@y.test", "@ejemplo.test",
                "userAgent", "\"ip\"", "email");
        assertThat(reply.json().get("items").get(0).get("details").get("kept").asString()).isEqualTo("si");
    }

    @Test
    void pagesOfFiftyWithCursor() {
        for (int i = 0; i < 60; i++) {
            seed("2026-01-02T10:00:00Z", alfa, "COMPANY_UPDATED", "{}");
        }
        String token = token(groupCookie, null);

        JsonNode first = activity(token, RANGE + "&action=COMPANY_UPDATED").json();
        assertThat(first.get("items")).hasSize(50);
        JsonNode second = activity(token, RANGE + "&action=COMPANY_UPDATED&cursor="
                + first.get("nextCursor").asString()).json();
        assertThat(second.get("items")).hasSize(10);
        assertThat(second.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void everyCallLeavesItsQueryEntryOnTheChatAdminChannel() {
        seed("2026-01-02T10:00:00Z", alfa, "COMPANY_UPDATED", "{}");
        String token = token(groupCookie, null);
        String prefix = token.substring("odin-adm_".length(), "odin-adm_".length() + 8);

        activity(token, RANGE + "&action=COMPANY_UPDATED");

        var entry = entries("CHAT_QUERY").getFirst();
        assertThat(entry.get("channel")).isEqualTo("CHAT_ADMIN");
        assertThat(entry.get("outcome")).isEqualTo("DONE");
        String details = entry.get("details").toString();
        assertThat(details).contains("\"rowCount\": 1", "\"prefix\": \"" + prefix + "\"", "COMPANY_UPDATED");
        assertThat(details).doesNotContain(token);
    }

    @Test
    void dailyCapIsPerKeyAndNearTheCapOnlyTheRemainderIsReturned() {
        for (int i = 0; i < 100; i++) {
            seed("2026-01-02T10:00:00Z", alfa, "COMPANY_UPDATED", "{}");
        }
        String token = token(groupCookie, null);
        String prefix = token.substring("odin-adm_".length(), "odin-adm_".length() + 8);
        String now = Instant.now().toString();
        seed(now, null, "CHAT_QUERY", "{\"prefix\":\"otra-clave\",\"rowCount\":500}", "CHAT_ADMIN");
        seed(now, null, "CHAT_QUERY", "{\"prefix\":\"" + prefix + "\",\"rowCount\":480}", "CHAT_ADMIN");
        String query = RANGE + "&action=COMPANY_UPDATED";

        JsonNode near = activity(token, query).json();
        assertThat(near.get("items")).hasSize(20);

        Reply capped = activity(token, query);
        assertThat(capped.status()).isEqualTo(429);
        assertThat(entries("CHAT_QUERY")).filteredOn(e -> e.get("outcome").equals("DENIED")).hasSize(1);
        assertThat(entries("CHAT_QUERY").getLast().get("details").toString()).contains("DAILY_CAP");
    }

    @Test
    void meDescribesBothKindsOfKey() {
        String group = token(groupCookie, null);
        JsonNode adminMe = api.get("/v1/me", "Authorization", "Bearer " + group).json();
        assertThat(adminMe.get("kind").asString()).isEqualTo("ADMIN_CHAT");
        assertThat(adminMe.get("role").asString()).isEqualTo("GROUP_ADMIN");
        assertThat(adminMe.get("company").isNull()).isTrue();
        assertThat(adminMe.get("topics")).isEmpty();

        String company = token(alfaCookie, null);
        JsonNode companyMe = api.get("/v1/me", "Authorization", "Bearer " + company).json();
        assertThat(companyMe.get("company").get("id").asString()).isEqualTo(alfa.toString());
        assertThat(companyMe.get("company").get("capabilities")).isEmpty();

        JsonNode workerMe = api.get("/v1/me", "Authorization", workerBearer).json();
        assertThat(workerMe.get("kind").asString()).isEqualTo("WORKER");
        assertThat(entries("CHAT_QUERY")).isEmpty();
    }
}
