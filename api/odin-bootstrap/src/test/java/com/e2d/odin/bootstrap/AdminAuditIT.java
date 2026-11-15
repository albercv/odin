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

/** Cada acción de administración hecha por HTTP deja exactamente una fila real, con su canal, objeto y detalles. */
@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class AdminAuditIT {

    private static final String SETUP_TOKEN = "setup-admin-audit-token";
    private static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");
    private static final String ADMIN_EMAIL = "admin@ejemplo.test";
    private static final String PASSWORD = "clave-muy-larga-1";
    private static final String ADMIN = "/v1/admin";

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

    private static String adminCookie;
    private static String companyId;
    private static String carlaId;
    private static String carlaCookie;
    private ApiClient api;
    private JdbcClient owner;

    @BeforeEach
    void setUpOnce() {
        api = new ApiClient(port);
        owner = JdbcClient.create(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        if (adminCookie == null) {
            installAndLogIn();
        }
        clearLog();
        owner.sql("UPDATE audit_setting SET enabled = true, usage_policy_reference = 'https://intranet.ejemplo.test/p'")
                .update();
    }

    private void installAndLogIn() {
        assertThat(api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo Ejemplo", "adminName",
                "Admin", "adminEmail", ADMIN_EMAIL, "password", PASSWORD)).status()).isEqualTo(201);
        adminCookie = login(ADMIN_EMAIL);
        companyId = admin("post", ADMIN + "/companies", map("name", "Alfa", "code", "ALFA01")).json().get("id")
                .asString();
        Reply carla = admin("post", ADMIN + "/companies/" + companyId + "/members", map("firstName", "Carla",
                "lastName", "Admin", "email", "carla@ejemplo.test", "roles", List.of("COMPANY_ADMIN"), "password",
                PASSWORD));
        carlaId = carla.json().get("member").get("userId").asString();
        carlaCookie = login("carla@ejemplo.test");
    }

    private String login(String email) {
        return api.post(ADMIN + "/session", map("email", email, "password", PASSWORD)).setCookie().split(";")[0];
    }

    private Reply admin(String method, String path, Object body) {
        return call(adminCookie, method, path, body);
    }

    private Reply call(String cookie, String method, String path, Object body) {
        Reply reply = switch (method) {
            case "post" -> api.post(path, body, "Cookie", cookie);
            case "put" -> api.put(path, body, "Cookie", cookie);
            case "patch" -> api.patch(path, body, "Cookie", cookie);
            default -> api.delete(path, "Cookie", cookie);
        };
        assertThat(reply.status()).as("%s %s -> %s", method, path, reply.body()).isBetween(200, 204);
        return reply;
    }

    /** La única fila de esa acción, que debe existir. */
    private Map<String, Object> only(String action) {
        return only(action, "PANEL");
    }

    private Map<String, Object> only(String action, String channel) {
        List<Map<String, Object>> rows = owner.sql("SELECT * FROM audit_event WHERE action = :a ORDER BY id")
                .param("a", action).query().listOfRows();
        assertThat(rows).as(action).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row).containsEntry("channel", channel).containsEntry("outcome", "DONE");
        assertThat(row.get("request_id")).isNotNull();
        return row;
    }

    private static JsonNode details(Map<String, Object> row) {
        return JsonMapper.builder().build().readTree(row.get("details").toString());
    }

    private void clearLog() {
        owner.sql("ALTER TABLE audit_event DISABLE TRIGGER USER").update();
        owner.sql("TRUNCATE audit_event, audit_actor").update();
        owner.sql("ALTER TABLE audit_event ENABLE TRIGGER USER").update();
    }

    private static void assertTarget(Map<String, Object> row, String kind, String id) {
        assertThat(row).containsEntry("target_kind", kind).containsEntry("target_id", id);
    }

    @Test
    void groupAdminActionsLeaveOneEntryEach() {
        String beta = admin("post", ADMIN + "/companies", map("name", "Beta", "code", "BETA01")).json().get("id")
                .asString();
        Map<String, Object> created = only("COMPANY_CREATED");
        assertThat(created).containsEntry("actor_role", "GROUP_ADMIN").containsEntry("target_label", "Beta");
        assertThat(created.get("company_id").toString()).isEqualTo(beta);
        assertTarget(created, "COMPANY", beta);

        admin("patch", ADMIN + "/companies/" + beta, map("name", "Beta SA"));
        assertThat(details(only("COMPANY_UPDATED")).get("nameChanged").asBoolean()).isTrue();

        admin("put", ADMIN + "/companies/" + beta + "/capabilities", map("capabilities", List.of("SEARCH_KNOWLEDGE")));
        JsonNode capabilities = details(only("COMPANY_CAPABILITIES_CHANGED"));
        assertThat(capabilities.get("before")).extracting(JsonNode::asString).containsExactly("CASE_FILES",
                "SAVE_KNOWLEDGE_CARD", "SEARCH_KNOWLEDGE", "SIMILAR_CASES", "UPLOAD_DOCUMENTS");
        assertThat(capabilities.get("after")).extracting(JsonNode::asString).containsExactly("SEARCH_KNOWLEDGE");

        String other = admin("post", ADMIN + "/group-admins", map("firstName", "Otra", "lastName", "Admin", "email",
                "otra@ejemplo.test", "password", PASSWORD)).json().get("admin").get("userId").asString();
        assertTarget(only("GROUP_ADMIN_ADDED"), "USER", other);
        admin("delete", ADMIN + "/group-admins/" + other, null);
        assertTarget(only("GROUP_ADMIN_REVOKED"), "USER", other);
    }

    private String newWorker() {
        String worker = admin("post", ADMIN + "/companies/" + companyId + "/members", map("firstName", "Wanda",
                "lastName", "Pérez", "email", "wanda" + System.nanoTime() + "@ejemplo.test", "roles",
                List.of("WORKER"))).json().get("member").get("userId").asString();
        clearLog();
        return worker;
    }

    @Test
    void memberAddLeavesNoPersonalData() {
        String worker = admin("post", ADMIN + "/companies/" + companyId + "/members", map("firstName", "Wanda",
                "lastName", "Pérez", "email", "wanda@ejemplo.test", "roles", List.of("WORKER"))).json().get("member")
                .get("userId").asString();

        Map<String, Object> added = only("MEMBER_ADDED");
        assertThat(added.get("company_id").toString()).isEqualTo(companyId);
        assertTarget(added, "USER", worker);
        assertThat(added.get("details").toString()).doesNotContain("wanda@ejemplo.test", "Wanda");
    }

    @Test
    void projectsAndTheirMembersLeaveOneEntryEach() {
        String worker = newWorker();
        String project = admin("post", ADMIN + "/companies/" + companyId + "/projects", map("name", "Obra Norte"))
                .json().get("id").asString();
        assertThat(only("PROJECT_CREATED")).containsEntry("target_label", "Obra Norte");
        String base = ADMIN + "/companies/" + companyId + "/projects/" + project;
        admin("patch", base, map("restricted", false));
        assertThat(details(only("PROJECT_UPDATED")).get("restricted").asBoolean()).isFalse();
        admin("put", base + "/members/" + worker, null);
        assertTarget(only("PROJECT_MEMBER_ADDED"), "USER", worker);
        admin("delete", base + "/members/" + worker, null);
        assertTarget(only("PROJECT_MEMBER_REMOVED"), "USER", worker);
        admin("delete", base, null);
        assertTarget(only("PROJECT_DELETED"), "PROJECT", project);
    }

    @Test
    void keysLeaveOnlyThePrefix() {
        String worker = newWorker();
        JsonNode key = admin("post", ADMIN + "/companies/" + companyId + "/members/" + worker + "/keys",
                map("label", "portátil de Wanda")).json();
        JsonNode issued = details(only("ACCESS_KEY_ISSUED"));
        assertThat(issued.get("prefix").asString()).isEqualTo(key.get("prefix").asString());
        assertThat(issued.toString()).doesNotContain(key.get("key").asString()).doesNotContain("portátil");
        admin("delete", ADMIN + "/keys/" + key.get("id").asString(), null);
        assertThat(details(only("ACCESS_KEY_REVOKED")).get("prefix").asString())
                .isEqualTo(key.get("prefix").asString());
    }

    @Test
    void personChangesLeaveFlagsAndNeverNamesOrEmails() {
        String worker = newWorker();
        admin("patch", ADMIN + "/companies/" + companyId + "/members/" + worker, map("active", false));
        JsonNode updated = details(only("MEMBER_UPDATED"));
        assertThat(updated.get("before").get("active").asBoolean()).isTrue();
        assertThat(updated.get("after").get("active").asBoolean()).isFalse();
        admin("patch", ADMIN + "/users/" + worker, map("firstName", "Wanda María", "email", "nuevo@ejemplo.test"));
        JsonNode user = details(only("USER_UPDATED"));
        assertThat(user.get("nameChanged").asBoolean()).isTrue();
        assertThat(user.get("emailChanged").asBoolean()).isTrue();
        assertThat(user.toString()).doesNotContain("ejemplo.test", "Wanda");
        admin("delete", ADMIN + "/companies/" + companyId + "/members/" + worker, null);
        assertTarget(only("MEMBER_REMOVED"), "USER", worker);
        admin("delete", ADMIN + "/users/" + worker, null);
        assertTarget(only("USER_DELETED"), "USER", worker);
    }

    @Test
    void passwordChangeRecordsNoPassword() {
        String dario = admin("post", ADMIN + "/companies/" + companyId + "/members", map("firstName", "Darío",
                "lastName", "Admin", "email", "dario@ejemplo.test", "roles", List.of("COMPANY_ADMIN"), "password",
                PASSWORD)).json().get("member").get("userId").asString();
        clearLog();

        admin("put", ADMIN + "/users/" + dario + "/password", map("newPassword", "otra-clave-larga-22"));

        Map<String, Object> row = only("PASSWORD_CHANGED");
        assertTarget(row, "USER", dario);
        assertThat(row.get("details").toString()).doesNotContain("otra-clave");
    }

    @Test
    void companyAdminLeavesHerCompanyOnTheEntry() {
        call(carlaCookie, "post", ADMIN + "/companies/" + companyId + "/projects", map("name", "Obra de Carla"));

        Map<String, Object> row = only("PROJECT_CREATED");
        assertThat(row).containsEntry("actor_role", "COMPANY_ADMIN");
        assertThat(row.get("company_id").toString()).isEqualTo(companyId);
        assertThat(row.get("actor_user_id").toString()).isEqualTo(carlaId);
    }

    @Test
    void loginAndLogoutLeaveEntriesWithClientDataOnly() {
        Reply ok = api.post(ADMIN + "/session", map("email", ADMIN_EMAIL, "password", PASSWORD), "User-Agent", "Firefox");
        String cookie = ok.setCookie().split(";")[0];
        JsonNode login = details(only("LOGIN_SUCCEEDED"));
        assertThat(login.get("userAgent").asString()).isEqualTo("Firefox");
        assertThat(login.get("ip").asString()).isNotBlank();

        api.delete(ADMIN + "/session", "Cookie", cookie, "User-Agent", "Firefox");
        assertThat(details(only("LOGOUT")).get("userAgent").asString()).isEqualTo("Firefox");
        assertThat(owner.sql("SELECT count(*) FROM audit_event WHERE jsonb_exists(details, 'ip')").query(Long.class).single())
                .isEqualTo(2L);
    }

    @Test
    void failedLoginsAreDeniedWithoutEmailAndKnownUserOnlyWhenItExists() {
        assertThat(api.post(ADMIN + "/session", map("email", ADMIN_EMAIL, "password", "mala"), "User-Agent", "Chrome")
                .status()).isEqualTo(401);
        assertThat(api.post(ADMIN + "/session", map("email", "nadie@ejemplo.test", "password", "mala")).status())
                .isEqualTo(401);

        List<Map<String, Object>> rows = owner.sql("SELECT * FROM audit_event ORDER BY id").query().listOfRows();
        assertThat(rows).extracting(r -> r.get("action")).containsExactly("LOGIN_FAILED", "LOGIN_FAILED");
        assertThat(rows).extracting(r -> r.get("outcome")).containsOnly("DENIED");
        assertThat(rows).extracting(r -> r.get("channel")).containsOnly("PANEL");
        assertThat(rows.get(0).get("actor_user_id")).isNotNull();
        assertThat(details(rows.get(0)).get("knownUser").asBoolean()).isTrue();
        assertThat(details(rows.get(0)).get("userAgent").asString()).isEqualTo("Chrome");
        assertThat(rows.get(1).get("actor_user_id")).isNull();
        assertThat(details(rows.get(1)).get("knownUser").asBoolean()).isFalse();
        rows.forEach(r -> assertThat(r.get("details").toString()).doesNotContain("ejemplo.test"));
    }

    @Test
    void setupEndpointsRecordTheInstallerThroughTheApiChannel() {
        assertThat(api.post("/v1/setup/admin-password", map("setupToken", "malo", "newPassword", PASSWORD)).status())
                .isEqualTo(403);
        assertThat(api.post("/v1/setup/admin-password", map("setupToken", SETUP_TOKEN, "newPassword", PASSWORD))
                .status()).isEqualTo(204);

        List<Map<String, Object>> rows = owner.sql("SELECT * FROM audit_event ORDER BY id").query().listOfRows();
        assertThat(rows).extracting(r -> r.get("action")).containsExactly("ADMIN_PASSWORD_RECOVERED",
                "ADMIN_PASSWORD_RECOVERED");
        assertThat(rows).extracting(r -> r.get("outcome")).containsExactly("DENIED", "DONE");
        assertThat(rows).extracting(r -> r.get("channel")).containsOnly("API");
        assertThat(rows).extracting(r -> r.get("actor_kind")).containsOnly("INSTALLER");
        assertThat(rows).extracting(r -> r.get("actor_user_id")).containsOnlyNulls();
        adminCookie = login(ADMIN_EMAIL);
    }

    private String uploadPdf(String project, String filename) {
        byte[] pdf = ("%PDF-1.4 contenido reservado " + System.nanoTime()).getBytes();
        Reply reply = api.upload(ADMIN + "/companies/" + companyId + "/documents?projectId=" + project, filename, pdf,
                "Cookie", adminCookie);
        assertThat(reply.status()).as(reply.body()).isEqualTo(201);
        return reply.json().get("document").get("id").asString();
    }

    @Test
    void documentActionsLeaveOneEntryEachAndNeverTheContent() {
        String project = admin("post", ADMIN + "/companies/" + companyId + "/projects", map("name", "Obra Sur")).json()
                .get("id").asString();
        clearLog();

        String document = uploadPdf(project, "informe.pdf");
        Map<String, Object> uploaded = only("DOCUMENT_UPLOADED");
        assertTarget(uploaded, "DOCUMENT", document);
        assertThat(uploaded.get("project_id").toString()).isEqualTo(project);
        JsonNode upload = details(uploaded);
        assertThat(upload.get("sha256").asString()).hasSize(64);
        assertThat(upload.get("sizeBytes").asInt()).isPositive();
        assertThat(uploaded.get("details").toString()).doesNotContain("reservado");
        AuditAssertions.assertNoForbiddenFields(upload);

        clearLog();
        assertThat(api.get(ADMIN + "/documents/" + document + "/original", "Cookie", adminCookie).status()).isEqualTo(200);
        assertThat(details(only("DOCUMENT_ORIGINAL_DOWNLOADED"))).isEmpty();

        admin("put", ADMIN + "/documents/" + document + "/withdrawal", null);
        assertTarget(only("DOCUMENT_WITHDRAWN"), "DOCUMENT", document);
        admin("delete", ADMIN + "/documents/" + document + "/withdrawal", null);
        assertTarget(only("DOCUMENT_RESTORED"), "DOCUMENT", document);

        admin("post", ADMIN + "/companies/" + companyId + "/documents/case-marks",
                map("documentIds", List.of(document), "caseDocument", true));
        JsonNode marks = details(only("DOCUMENTS_CASE_MARK_CHANGED"));
        assertThat(marks.get("count").asInt()).isEqualTo(1);
        assertThat(marks.get("ids").get(0).asString()).isEqualTo(document);
    }

    @Test
    void topicsLeaveOneEntryEachWithTheNameAsLabelOnly() {
        String project = admin("post", ADMIN + "/companies/" + companyId + "/projects", map("name", "Obra Este")).json()
                .get("id").asString();
        String document = uploadPdf(project, "tema.pdf");
        clearLog();

        String topic = admin("post", ADMIN + "/companies/" + companyId + "/topics",
                map("name", "Agua", "definition", "Fugas y humedades")).json().get("id").asString();
        Map<String, Object> created = only("TOPIC_CREATED");
        assertTarget(created, "TOPIC", topic);
        assertThat(created).containsEntry("target_label", "Agua");
        assertThat(created.get("details").toString()).doesNotContain("Fugas");

        admin("put", ADMIN + "/documents/" + document + "/topics/" + topic, null);
        Map<String, Object> added = only("DOCUMENT_TOPIC_ADDED");
        assertTarget(added, "TOPIC", topic);
        assertThat(details(added).get("documentId").asString()).isEqualTo(document);
        admin("delete", ADMIN + "/documents/" + document + "/topics/" + topic, null);
        assertTarget(only("DOCUMENT_TOPIC_REMOVED"), "TOPIC", topic);
        admin("post", ADMIN + "/documents/" + document + "/topics/retag", null);
        assertTarget(only("DOCUMENT_RETAGGED"), "DOCUMENT", document);

        admin("patch", ADMIN + "/companies/" + companyId + "/topics/" + topic, map("definition", "Otra definición"));
        assertThat(details(only("TOPIC_UPDATED")).get("definitionChanged").asBoolean()).isTrue();
        admin("delete", ADMIN + "/companies/" + companyId + "/topics/" + topic, null);
        assertTarget(only("TOPIC_DELETED"), "TOPIC", topic);
    }

    private record WorkerSetup(String project, String caseId, String workerId, String bearer) {
    }

    private WorkerSetup workerSetup() {
        admin("put", ADMIN + "/companies/" + companyId + "/capabilities", map("capabilities",
                List.of("SEARCH_KNOWLEDGE", "SAVE_KNOWLEDGE_CARD", "CASE_FILES", "UPLOAD_DOCUMENTS")));
        String project = admin("post", ADMIN + "/companies/" + companyId + "/projects", map("name", "Obra " + System.nanoTime()))
                .json().get("id").asString();
        String caseId = uploadPdf(project, "ficha.pdf");
        admin("post", ADMIN + "/companies/" + companyId + "/documents/case-marks",
                map("documentIds", List.of(caseId), "caseDocument", true));
        String workerId = admin("post", ADMIN + "/companies/" + companyId + "/members", map("firstName", "Marta",
                "lastName", "Gil", "email", "marta" + System.nanoTime() + "@ejemplo.test", "roles", List.of("WORKER"), "projectIds",
                List.of(project))).json().get("member").get("userId").asString();
        String bearer = "Bearer " + admin("post", ADMIN + "/companies/" + companyId + "/members/" + workerId + "/keys",
                map("label", "portatil")).json().get("key").asString();
        clearLog();
        return new WorkerSetup(project, caseId, workerId, bearer);
    }

    @Test
    void workerKeyActionsAreRecordedThroughTheChatChannel() {
        WorkerSetup w = workerSetup();

        assertThat(api.post("/v1/cards", map("title", "TITULO-RESERVADO", "body", "TEXTO-RESERVADO", "projectId",
                w.project()), "Authorization", w.bearer()).status()).isEqualTo(201);
        Map<String, Object> card = only("CARD_SAVED", "CHAT");
        assertThat(card).containsEntry("actor_kind", "PERSON").containsEntry("actor_role", "WORKER");
        assertThat(card.get("actor_user_id").toString()).isEqualTo(w.workerId());
        assertThat(card.get("project_id").toString()).isEqualTo(w.project());
        assertThat(card.get("details").toString()).doesNotContain("RESERVADO");
        assertThat(card.get("target_label")).isNull();

        api.post("/v1/cases/" + w.caseId() + "/upload-links", map(), "Authorization", w.bearer());
        Map<String, Object> issued = only("UPLOAD_LINK_ISSUED", "CHAT");
        assertThat(issued).containsEntry("actor_role", "WORKER");
        assertThat(details(issued).get("linkId").asString()).isNotBlank();
    }

    @Test
    void anUploadByLinkIsRecordedAsTheLinkWithItsIssuingWorker() {
        WorkerSetup w = workerSetup();
        String url = api.post("/v1/cases/" + w.caseId() + "/upload-links", map(), "Authorization", w.bearer())
                .json().get("url").asString();
        String linkId = details(only("UPLOAD_LINK_ISSUED", "CHAT")).get("linkId").asString();

        Reply sent = new ApiClient("127.0.0.1", port).upload(
                "/v1/upload-links/" + url.substring(url.lastIndexOf('/') + 1) + "/documents", "anexo.pdf",
                ("%PDF-1.4 anexo reservado " + System.nanoTime()).getBytes(), "X-Forwarded-For", "192.168.1.20");

        assertThat(sent.status()).as(sent.body()).isEqualTo(200);
        Map<String, Object> byLink = only("DOCUMENT_UPLOADED", "API");
        assertThat(byLink).containsEntry("actor_kind", "LINK").containsEntry("via", linkId);
        assertThat(byLink.get("actor_user_id")).isNull();
        assertThat(byLink.get("actor_role")).isNull();
        assertThat(details(byLink).get("linkOwnerId").asString()).isEqualTo(w.workerId());
        assertThat(details(byLink).get("duplicate").asBoolean()).isFalse();
        assertThat(byLink.get("details").toString()).doesNotContain("reservado");
        AuditAssertions.assertNoForbiddenFields(details(byLink));
    }
}
