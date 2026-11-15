package com.e2d.odin.bootstrap;

import static com.e2d.odin.bootstrap.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.bootstrap.ApiClient.Reply;
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

/** Consulta, exportación e integridad del registro por HTTP, contra una base real. */
@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class ActivityIT {

    private static final String SETUP_TOKEN = "setup-activity-token";
    private static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");
    private static final String PASSWORD = "clave-muy-larga-1";
    private static final String URL = "/v1/admin/activity";

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
    private ApiClient api;
    private JdbcClient owner;

    @BeforeEach
    void setUpOnce() {
        api = new ApiClient(port);
        owner = JdbcClient.create(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        if (groupCookie == null) {
            install();
        }
        clearLog();
        owner.sql("UPDATE audit_setting SET enabled = true, usage_policy_reference = 'https://politica.test'")
                .update();
    }

    private void install() {
        api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo", "adminName", "Admin",
                "adminEmail", "admin@ejemplo.test", "password", PASSWORD));
        groupCookie = login("admin@ejemplo.test");
        alfa = UUID.fromString(company("Alfa", "ALFA01"));
        beta = UUID.fromString(company("Beta", "BETA01"));
        alfaCookie = login(member(alfa, "carla@ejemplo.test", "COMPANY_ADMIN"));
        betaCookie = login(member(beta, "dani@ejemplo.test", "COMPANY_ADMIN"));
        String worker = api.post("/v1/admin/companies/" + alfa + "/members", map("firstName", "Marta", "lastName",
                "Gil", "email", "marta@ejemplo.test", "roles", List.of("WORKER")), "Cookie", groupCookie).json()
                .get("member").get("userId").asString();
        workerBearer = "Bearer " + api.post("/v1/admin/companies/" + alfa + "/members/" + worker + "/keys",
                map("label", "portatil"), "Cookie", groupCookie).json().get("key").asString();
    }

    private String company(String name, String code) {
        return api.post("/v1/admin/companies", map("name", name, "code", code), "Cookie", groupCookie).json()
                .get("id").asString();
    }

    private String member(UUID company, String email, String role) {
        api.post("/v1/admin/companies/" + company + "/members", map("firstName", "Ana", "lastName", "Admin",
                "email", email, "roles", List.of(role), "password", PASSWORD), "Cookie", groupCookie);
        return email;
    }

    private String login(String email) {
        return api.post("/v1/admin/session", map("email", email, "password", PASSWORD)).setCookie().split(";")[0];
    }

    private void clearLog() {
        owner.sql("ALTER TABLE audit_event DISABLE TRIGGER USER").update();
        owner.sql("TRUNCATE audit_event, audit_actor").update();
        owner.sql("ALTER TABLE audit_event ENABLE TRIGGER USER").update();
    }

    private long seed(String at, UUID company, String action) {
        return seed(at, company, action, "SYSTEM", null, "SYSTEM", "DONE", "{}", null);
    }

    private long seed(String at, UUID company, String action, String kind, UUID actor, String channel,
            String outcome, String details, String label) {
        return owner.sql("""
                INSERT INTO audit_event (occurred_at, company_id, actor_kind, actor_user_id, channel, action, outcome,
                    details, target_label, chain_key, prev_hash, hash)
                VALUES (CAST(:at AS timestamptz), :company, :kind, :actor, :channel, :action, :outcome,
                    CAST(:details AS jsonb), :label, 'main', :prev, :hash) RETURNING id""")
                .param("at", at).param("company", company, java.sql.Types.OTHER).param("kind", kind)
                .param("actor", actor, java.sql.Types.OTHER).param("channel", channel).param("action", action)
                .param("outcome", outcome).param("details", details).param("label", label)
                .param("prev", UUID.randomUUID().toString()).param("hash", UUID.randomUUID().toString())
                .query(Long.class).single();
    }

    private JsonNode get(String cookie, String query) {
        Reply reply = api.get(URL + query, "Cookie", cookie);
        assertThat(reply.status()).as(reply.body()).isEqualTo(200);
        return reply.json();
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("items").forEach(row -> ids.add(row.get("id").asLong()));
        return ids;
    }

    @Test
    void workerIsRejected() {
        seed("2026-01-01T10:00:00Z", alfa, "COMPANY_UPDATED");

        assertThat(api.get(URL, "Authorization", workerBearer).status()).isIn(401, 403);
        assertThat(api.get(URL + ".csv", "Authorization", workerBearer).status()).isIn(401, 403);
        assertThat(api.get(URL + "/integrity", "Authorization", workerBearer).status()).isIn(401, 403);
        assertThat(api.get(URL).status()).isEqualTo(401);
    }

    @Test
    void companyAdminSeesOnlyHerCompanyAndNeverGroupRows() {
        long a = seed("2026-01-01T10:00:00Z", alfa, "COMPANY_UPDATED");
        long b = seed("2026-01-01T10:01:00Z", beta, "COMPANY_UPDATED");
        long none = seed("2026-01-01T10:02:00Z", null, "AUDIT_ENABLED");

        assertThat(ids(get(alfaCookie, ""))).containsExactly(a);
        assertThat(ids(get(betaCookie, ""))).containsExactly(b);
        assertThat(ids(get(groupCookie, ""))).containsExactly(none, b, a);
        assertThat(api.get(URL + ".csv", "Cookie", alfaCookie).body()).doesNotContain("AUDIT_ENABLED");
    }

    @Test
    void foreignCompanyFilterIsForbidden() {
        assertThat(api.get(URL + "?companyId=" + beta, "Cookie", alfaCookie).status()).isEqualTo(403);
        assertThat(api.get(URL + ".csv?companyId=" + beta, "Cookie", alfaCookie).status()).isEqualTo(403);
        assertThat(get(alfaCookie, "?companyId=" + alfa).get("items")).isEmpty();
        assertThat(get(groupCookie, "?companyId=" + beta).get("items")).isEmpty();
    }

    @Test
    void eachFilterNarrows() {
        UUID person = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        seed("2026-01-01T10:00:00Z", alfa, "COMPANY_UPDATED");
        long chat = seed("2026-02-01T10:00:00Z", alfa, "CARD_SAVED", "PERSON", person, "CHAT", "DENIED", "{}", null);
        owner.sql("ALTER TABLE audit_event DISABLE TRIGGER USER").update();
        owner.sql("UPDATE audit_event SET project_id = :p WHERE id = :id").param("p", project).param("id", chat)
                .update();
        owner.sql("ALTER TABLE audit_event ENABLE TRIGGER USER").update();
        seed("2026-03-01T10:00:00Z", beta, "COMPANY_UPDATED");

        assertThat(ids(get(groupCookie, "?action=CARD_SAVED"))).containsExactly(chat);
        assertThat(ids(get(groupCookie, "?channel=CHAT"))).containsExactly(chat);
        assertThat(ids(get(groupCookie, "?outcome=DENIED"))).containsExactly(chat);
        assertThat(ids(get(groupCookie, "?actorUserId=" + person))).containsExactly(chat);
        assertThat(ids(get(groupCookie, "?projectId=" + project))).containsExactly(chat);
        assertThat(get(groupCookie, "?companyId=" + beta).get("items")).hasSize(1);
        assertThat(get(groupCookie, "?from=2026-02-01T10:00:00Z").get("items")).hasSize(2);
        assertThat(get(groupCookie, "?to=2026-02-01T10:00:00Z").get("items")).hasSize(1);
        assertThat(get(groupCookie, "?from=2026-02-01T00:00:00Z&to=2026-03-01T10:00:00Z").get("items")).hasSize(1);
        assertThat(api.get(URL + "?channel=NOPE", "Cookie", groupCookie).status()).isEqualTo(400);
        assertThat(api.get(URL + "?cursor=basura", "Cookie", groupCookie).status()).isEqualTo(400);
    }

    @Test
    void paginationIsStableWithManyEntriesAtTheSameInstant() {
        owner.sql("""
                INSERT INTO audit_event (occurred_at, company_id, actor_kind, channel, action, outcome, chain_key,
                    prev_hash, hash)
                SELECT timestamptz '2026-01-01T10:00:00Z' + (n / 40) * interval '1 second', :company, 'SYSTEM',
                    'SYSTEM', 'COMPANY_UPDATED', 'DONE', 'main', gen_random_uuid()::text, gen_random_uuid()::text
                FROM generate_series(0, 119) n""").param("company", alfa).update();
        List<Long> expected = owner.sql("SELECT id FROM audit_event ORDER BY occurred_at DESC, id DESC")
                .query(Long.class).list();

        List<Long> seen = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode page = get(groupCookie, cursor == null ? "" : "?cursor=" + cursor);
            assertThat(page.get("items").size()).isLessThanOrEqualTo(50);
            seen.addAll(ids(page));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
        } while (cursor != null);

        assertThat(seen).containsExactlyElementsOf(expected);
    }

    @Test
    void actorNameComesFromAuditActorOrSaysSuppressedAndNoEmailAppears() {
        UUID known = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        owner.sql("INSERT INTO audit_actor (user_id, display_name, email, updated_at) VALUES (:id, 'Carla Ruiz', 'carla.secreta@ejemplo.test', now())")
                .param("id", known).update();
        seed("2026-01-01T10:00:00Z", alfa, "CARD_SAVED", "PERSON", known, "CHAT", "DONE", "{}", null);
        seed("2026-01-01T10:01:00Z", alfa, "CARD_SAVED", "PERSON", gone, "CHAT", "DONE", "{}", null);
        seed("2026-01-01T10:02:00Z", alfa, "CARD_SAVED", "SYSTEM", null, "SYSTEM", "DONE", "{}", null);

        Reply reply = api.get(URL, "Cookie", groupCookie);
        List<String> names = new ArrayList<>();
        reply.json().get("items").forEach(r -> names.add(r.get("actorName").isNull() ? null : r.get("actorName").asString()));
        assertThat(names).containsExactly(null, "Persona suprimida", "Carla Ruiz");
        assertThat(reply.body()).doesNotContainIgnoringCase("email").doesNotContain("secreta");
        Reply csv = api.get(URL + ".csv", "Cookie", alfaCookie);
        assertThat(csv.body()).doesNotContainIgnoringCase("email").doesNotContain("secreta").contains("Carla Ruiz");
    }

    @Test
    void csvHasHeadHashEscapesAndNeutralisesFormulas() {
        seed("2026-01-01T10:00:00Z", alfa, "COMPANY_UPDATED", "SYSTEM", null, "SYSTEM", "DONE", "{\"a\": 1}",
                "=HYPERLINK(\"x\")");
        seed("2026-01-01T10:01:00Z", alfa, "COMPANY_UPDATED", "SYSTEM", null, "SYSTEM", "DONE", "{}",
                "uno,\"dos\"\ntres");
        seed("2026-01-01T10:02:00Z", alfa, "COMPANY_UPDATED", "SYSTEM", null, "SYSTEM", "DONE", "{}", "@cmd");
        JsonNode integrity = api.get(URL + "/integrity", "Cookie", groupCookie).json();

        Reply csv = api.get(URL + ".csv", "Cookie", groupCookie);

        assertThat(csv.status()).isEqualTo(200);
        assertThat(csv.contentType()).startsWith("text/csv");
        assertThat(csv.body()).startsWith("# cabeza: " + integrity.get("headHash").asString() + " "
                + integrity.get("headAt").asString().substring(0, 19));
        assertThat(csv.body()).contains("\"'=HYPERLINK(\"\"x\"\")\"", "\"uno,\"\"dos\"\"\ntres\"", "'@cmd",
                "\"{\"\"a\"\":1}\"");
    }

    @Test
    void csvIsCappedAtTenThousandRows() {
        owner.sql("""
                INSERT INTO audit_event (occurred_at, company_id, actor_kind, channel, action, outcome, chain_key,
                    prev_hash, hash)
                SELECT timestamptz '2026-01-01T00:00:00Z' + n * interval '1 second', :company, 'SYSTEM', 'SYSTEM',
                    'COMPANY_UPDATED', 'DONE', 'main', gen_random_uuid()::text, gen_random_uuid()::text
                FROM generate_series(1, 10001) n""").param("company", alfa).update();

        String body = api.get(URL + ".csv", "Cookie", alfaCookie).body();

        assertThat(body.lines().filter(l -> l.contains("COMPANY_UPDATED")).count()).isEqualTo(10_000);
        assertThat(body.lines().reduce((a, b) -> b).orElseThrow()).startsWith("# truncado");
    }

    @Test
    void exportLeavesItsOwnEntryWithTheFilters() {
        api.get(URL + ".csv?action=COMPANY_UPDATED&companyId=" + alfa + "&channel=SYSTEM", "Cookie", alfaCookie);

        Map<String, Object> row = owner.sql("SELECT * FROM audit_event WHERE action = 'ACTIVITY_EXPORTED'").query()
                .singleRow();
        assertThat(row).containsEntry("actor_role", "COMPANY_ADMIN").containsEntry("channel", "PANEL");
        assertThat(row.get("company_id").toString()).isEqualTo(alfa.toString());
        assertThat(row.get("details").toString()).contains("\"action\": \"COMPANY_UPDATED\"", "\"channel\": \"SYSTEM\"",
                alfa.toString());
        assertThat(get(alfaCookie, "?action=ACTIVITY_EXPORTED").get("items")).hasSize(1);
    }

    @Test
    void integrityIsHealthyThenPointsAtTheTamperedEntry() {
        newCompany();
        newCompany();
        long first = owner.sql("SELECT min(id) FROM audit_event").query(Long.class).single();
        long last = owner.sql("SELECT max(id) FROM audit_event").query(Long.class).single();

        for (String cookie : List.of(groupCookie, alfaCookie)) {
            JsonNode ok = api.get(URL + "/integrity", "Cookie", cookie).json();
            assertThat(ok.get("logEnabled").asBoolean()).isTrue();
            assertThat(ok.get("intact").asBoolean()).isTrue();
            assertThat(ok.get("firstBrokenId").isNull()).isTrue();
            assertThat(ok.get("headId").asLong()).isEqualTo(last);
            assertThat(ok.get("headHash").asString()).hasSize(64);
            assertThat(ok.get("headAt").isNull()).isFalse();
        }
        JsonNode sealed = api.get(URL + "/integrity", "Cookie", groupCookie).json();

        owner.sql("ALTER TABLE audit_event DISABLE TRIGGER USER").update();
        owner.sql("UPDATE audit_event SET target_label = 'otro' WHERE id = :id").param("id", first).update();
        owner.sql("ALTER TABLE audit_event ENABLE TRIGGER USER").update();

        JsonNode broken = api.get(URL + "/integrity", "Cookie", groupCookie).json();
        assertThat(broken.get("intact").asBoolean()).isFalse();
        assertThat(broken.get("firstBrokenId").asLong()).isEqualTo(first);
        assertThat(seal(sealed.get("headId").asLong(), sealed.get("headHash").asString())).isFalse();
    }

    @Test
    void sealCheckMatchesOnlyTheSavedHash() {
        newCompany();
        JsonNode head = api.get(URL + "/integrity", "Cookie", groupCookie).json();

        assertThat(seal(head.get("headId").asLong(), head.get("headHash").asString())).isTrue();
        assertThat(seal(head.get("headId").asLong(), "0".repeat(64))).isFalse();
        assertThat(seal(head.get("headId").asLong() + 1, head.get("headHash").asString())).isFalse();
        assertThat(api.post(URL + "/seal-check", map("id", 1, "hash", "x"), "Authorization", workerBearer).status())
                .isIn(401, 403);
    }

    @Test
    void integrityWithTheLogOffSaysSo() {
        owner.sql("UPDATE audit_setting SET enabled = false").update();

        JsonNode state = api.get(URL + "/integrity", "Cookie", groupCookie).json();

        assertThat(state.get("logEnabled").asBoolean()).isFalse();
        assertThat(state.get("intact").asBoolean()).isTrue();
        assertThat(state.get("headId").isNull()).isTrue();
    }

    private void newCompany() {
        String code = Long.toString(System.nanoTime(), 36).toUpperCase().substring(0, 6);
        assertThat(api.post("/v1/admin/companies", map("name", "Nueva " + code, "code", code), "Cookie", groupCookie)
                .status()).isEqualTo(201);
    }

    private boolean seal(long id, String hash) {
        return api.post(URL + "/seal-check", map("id", id, "hash", hash), "Cookie", groupCookie).json()
                .get("matches").asBoolean();
    }
}
