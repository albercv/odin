package com.e2d.odin.bootstrap;

import static com.e2d.odin.bootstrap.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.bootstrap.ApiClient.Reply;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
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

/** El administrador confirma o descarta un vínculo detectado: lo decide una persona (D-050), con registro. */
@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class DocumentLinkReviewIT {

    private static final String SETUP_TOKEN = "setup-link-review";
    private static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");
    private static final String PASSWORD = "clave-muy-larga-1";

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
        registry.add("odin.links.poll-interval", () -> "PT1S");
    }

    @LocalServerPort int port;

    private static String adminCookie;
    private static String company;
    private static String worker;
    private static String otherAdminCookie;
    private static int counter;
    private ApiClient api;
    private JdbcClient owner;
    private String code;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        owner = JdbcClient.create(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        if (adminCookie == null) {
            install();
        }
        code = "ZZR-" + (++counter) + "-4711";
        owner.sql("UPDATE audit_setting SET enabled = true, usage_policy_reference = 'https://intranet.ejemplo.test/p'")
                .update();
    }

    private void install() {
        assertThat(api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo", "adminName", "Admin",
                "adminEmail", "admin@ejemplo.test", "password", PASSWORD)).status()).isEqualTo(201);
        adminCookie = login("admin@ejemplo.test");
        company = company("Alfa", "ALFA01");
        worker = "Bearer " + api.post("/v1/admin/companies/" + company + "/members/" + member(company,
                "marta@ejemplo.test", "WORKER") + "/keys", map("label", "pc"), "Cookie", adminCookie).json()
                .get("key").asString();
        String beta = company("Beta", "BETA01");
        member(beta, "ana@ejemplo.test", "COMPANY_ADMIN");
        otherAdminCookie = login("ana@ejemplo.test");
        api.put("/v1/admin/companies/" + company + "/capabilities", map("capabilities", List.of("SEARCH_KNOWLEDGE")),
                "Cookie", adminCookie);
    }

    private String login(String email) {
        return api.post("/v1/admin/session", map("email", email, "password", PASSWORD)).setCookie().split(";")[0];
    }

    private String company(String name, String companyCode) {
        return api.post("/v1/admin/companies", map("name", name, "code", companyCode), "Cookie", adminCookie).json()
                .get("id").asString();
    }

    private String member(String of, String email, String role) {
        Map<String, Object> body = new java.util.LinkedHashMap<>(map("firstName", "Persona", "lastName", "Gil",
                "email", email, "roles", List.of(role), "projectIds", List.of()));
        if (!"WORKER".equals(role)) {
            body.put("password", PASSWORD);
        }
        Reply added = api.post("/v1/admin/companies/" + of + "/members", body, "Cookie", adminCookie);
        assertThat(added.status()).isEqualTo(201);
        return added.json().get("member").get("userId").asString();
    }

    private String upload(String title, String text) throws InterruptedException {
        Reply reply = api.upload("/v1/admin/companies/" + company + "/documents?caseDocument=false", title + ".txt",
                text.getBytes(StandardCharsets.UTF_8), "Cookie", adminCookie);
        assertThat(reply.status()).isEqualTo(201);
        String id = reply.json().get("document").get("id").asString();
        eventually(() -> "READY".equals(admin("/v1/admin/documents/" + id).json().get("status").asString()));
        return id;
    }

    private Reply admin(String path) {
        return api.get(path, "Cookie", adminCookie);
    }

    private static void eventually(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 80 && !condition.getAsBoolean(); i++) {
            Thread.sleep(250);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    /** Dos facturas: la rectificativa cita a la original y el vínculo ya está resuelto. */
    private String[] invoicePair() throws InterruptedException {
        String invoice = upload("Factura " + code, "Número de factura: " + code + "\nTotal 100 euros");
        String fix = upload("Rectificativa " + code, "Número de factura rectificativa: " + code + "-R\n"
                + "Rectifica la factura " + code + ": importe correcto 90 euros.");
        eventually(() -> link(fix, "RECTIFIES") != null && link(fix, "RECTIFIES").get("documentId") != null
                && !link(fix, "RECTIFIES").get("documentId").isNull());
        return new String[] {invoice, fix};
    }

    private JsonNode link(String documentId, String relation) {
        Reply reply = admin("/v1/admin/documents/" + documentId + "/context");
        if (reply.status() != 200) {
            return null;
        }
        for (JsonNode l : reply.json().get("links")) {
            if (relation.equals(l.get("relation").asString())) {
                return l;
            }
        }
        return null;
    }

    private JsonNode workerCorrections(String documentId) {
        return api.get("/v1/documents/" + documentId + "/pages/1", "Authorization", worker).json().get("corrections");
    }

    private Reply review(String linkId, String verb, String cookie) {
        return api.post("/v1/admin/document-links/" + linkId + "/" + verb, map(), "Cookie", cookie);
    }

    @Test
    void confirmingARectificationMarksTheSupersessionAndTheWorkerSeesItConfirmed() throws Exception {
        String[] pair = invoicePair();
        JsonNode detected = link(pair[1], "RECTIFIES");
        assertThat(detected.get("status").asString()).isEqualTo("DETECTED");
        assertThat(detected.get("direction").asString()).isEqualTo("corrects");
        assertThat(detected.get("evidenceQuote").asString()).contains("Rectifica la factura " + code);
        assertThat(detected.get("evidencePage").asInt()).isEqualTo(1);
        assertThat(workerCorrections(pair[0]).get(0).get("confirmed").asBoolean()).isFalse();

        assertThat(review(detected.get("id").asString(), "confirm", adminCookie).status()).isEqualTo(204);

        assertThat(link(pair[1], "RECTIFIES").get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(admin("/v1/admin/documents/" + pair[0]).json().get("supersededBy").get("id").asString())
                .isEqualTo(pair[1]);
        assertThat(workerCorrections(pair[0]).get(0).get("confirmed").asBoolean()).isTrue();
        assertThat(admin("/v1/admin/documents/" + pair[0] + "/context").json().get("versions")).hasSize(2);
    }

    @Test
    void dismissingALinkHidesItFromTheWorkerButNotFromTheAdmin() throws Exception {
        String[] pair = invoicePair();
        assertThat(workerCorrections(pair[0])).hasSize(1);

        String id = link(pair[1], "RECTIFIES").get("id").asString();
        assertThat(review(id, "dismiss", adminCookie).status()).isEqualTo(204);

        assertThat(workerCorrections(pair[0])).isEmpty();
        assertThat(link(pair[1], "RECTIFIES").get("status").asString()).isEqualTo("DISMISSED");
        assertThat(admin("/v1/admin/documents/" + pair[0]).json().get("supersededBy").isNull()).isTrue();
    }

    @Test
    void aRescanKeepsTheReviewOfTheSameLink() throws Exception {
        String[] kept = invoicePair();
        String keptId = link(kept[1], "RECTIFIES").get("id").asString();
        review(keptId, "confirm", adminCookie);
        code = "ZZR-" + (++counter) + "-4711";
        String[] dropped = invoicePair();
        review(link(dropped[1], "RECTIFIES").get("id").asString(), "dismiss", adminCookie);

        owner.sql("DELETE FROM document_link_scan").update();
        eventually(() -> owner.sql("SELECT count(*) FROM document_link_scan").query(Integer.class).single() >= 4);

        assertThat(link(kept[1], "RECTIFIES").get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(link(dropped[1], "RECTIFIES").get("status").asString()).isEqualTo("DISMISSED");
        assertThat(owner.sql("SELECT count(*) FROM document_link WHERE source_document_id = CAST(:s AS uuid)")
                .param("s", dropped[1]).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void aWorkerAndTheAdminOfAnotherCompanyAreRejected() throws Exception {
        String[] pair = invoicePair();
        String id = link(pair[1], "RECTIFIES").get("id").asString();

        assertThat(api.get("/v1/admin/documents/" + pair[0] + "/context", "Authorization", worker).status())
                .isIn(401, 403);
        assertThat(api.post("/v1/admin/document-links/" + id + "/confirm", map(), "Authorization", worker).status())
                .isIn(401, 403);
        assertThat(api.get("/v1/admin/documents/" + pair[0] + "/context", "Cookie", otherAdminCookie).status())
                .isEqualTo(404);
        assertThat(review(id, "confirm", otherAdminCookie).status()).isEqualTo(404);
        assertThat(review(id, "dismiss", otherAdminCookie).status()).isEqualTo(404);
        assertThat(link(pair[1], "RECTIFIES").get("status").asString()).isEqualTo("DETECTED");
    }

    @Test
    void confirmedAndDismissedAreAuditedWithTheLinkIdAndNoDocumentText() throws Exception {
        String[] pair = invoicePair();
        String id = link(pair[1], "RECTIFIES").get("id").asString();
        review(id, "confirm", adminCookie);
        String[] other = invoicePairAgain();
        String otherId = link(other[1], "RECTIFIES").get("id").asString();
        review(otherId, "dismiss", adminCookie);

        assertAudit("DOCUMENT_LINK_CONFIRMED", id);
        assertAudit("DOCUMENT_LINK_DISMISSED", otherId);
        assertThat(owner.sql("SELECT count(*) FROM audit_event WHERE action = 'DOCUMENT_SUPERSEDED'")
                .query(Integer.class).single()).isPositive();
    }

    private String[] invoicePairAgain() throws InterruptedException {
        code = "ZZR-" + (++counter) + "-4711";
        return invoicePair();
    }

    private void assertAudit(String action, String linkId) {
        List<Map<String, Object>> rows = owner.sql("SELECT * FROM audit_event WHERE action = :a AND target_id = :id")
                .param("a", action).param("id", linkId).query().listOfRows();
        assertThat(rows).hasSize(1);
        String details = rows.get(0).get("details").toString();
        assertThat(rows.get(0)).containsEntry("target_kind", "DOCUMENT_LINK").containsEntry("outcome", "DONE");
        assertThat(details).doesNotContain("Rectifica").doesNotContain("Factura").doesNotContain("importe");
    }
}
