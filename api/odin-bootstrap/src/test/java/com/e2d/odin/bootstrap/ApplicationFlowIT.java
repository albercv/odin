package com.e2d.odin.bootstrap;

import static com.e2d.odin.bootstrap.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.bootstrap.ApiClient.Reply;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;

@TestMethodOrder(OrderAnnotation.class)
class ApplicationFlowIT extends AbstractIT {

    private static final String TEXT = "El contrato de mantenimiento del ascensor vence el quince de marzo. "
            + "La empresa ficticia Alfa Ejemplos renueva cada año.";
    private static final String ADMIN_EMAIL = "admin@ejemplo.test";
    private static final String ADMIN_PASSWORD = "clave-muy-larga-1";

    private static String adminCookie;
    private static String companyA;
    private static String companyB;
    private static String keyA;
    private static String keyB;
    private static String documentA;

    @LocalServerPort int port;

    @Test
    @Order(1)
    void fullFlowKeepsCompaniesIsolated() throws InterruptedException {
        ApiClient api = new ApiClient(port);
        setUpAndLogIn(api);
        companyA = createCompany(api, "Empresa A", "EA");
        companyB = createCompany(api, "Empresa B", "EB");
        keyA = workerKey(api, companyA);
        keyB = workerKey(api, companyB);

        Reply upload = api.upload("/v1/admin/companies/" + companyA + "/documents", "contrato.txt",
                TEXT.getBytes(StandardCharsets.UTF_8), "Cookie", adminCookie);
        assertThat(upload.status()).isEqualTo(201);
        documentA = upload.json().get("document").get("id").asString();
        awaitReady(api, documentA);

        Reply found = api.post("/v1/search", map("queries", List.of("contrato mantenimiento ascensor")),
                "Authorization", keyA);
        assertThat(found.json().get("noSource").asBoolean()).isFalse();
        assertThat(found.json().get("hits").get(0).get("sourceId").asString()).isEqualTo(documentA);
        Reply cited = api.post("/v1/citations/verify",
                map("documentId", documentA, "pageNumber", 1, "quote", "vence el  quince DE marzo"),
                "Authorization", keyA);
        assertThat(cited.json().get("verified").asBoolean()).isTrue();

        Reply other = api.post("/v1/search", map("queries", List.of("contrato mantenimiento ascensor")),
                "Authorization", keyB);
        assertThat(other.json().get("noSource").asBoolean()).isTrue();
        assertThat(api.get("/v1/documents/" + documentA + "/pages/1", "Authorization", keyB).status())
                .isEqualTo(404);
        assertThat(api.get("/v1/documents/" + documentA + "/pages/1", "Authorization", keyA).status())
                .isEqualTo(200);
        approveCard(api);
    }

    @Test
    @Order(2)
    void filterRejectionsAreProblemJson() {
        ApiClient api = new ApiClient(port);
        Reply noSession = api.get("/v1/admin/me");
        Reply wrongOrigin = api.post("/v1/admin/session", map("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD),
                "Origin", "http://evil.test");
        Reply noKey = api.get("/v1/me");
        assertThat(List.of(noSession.status(), wrongOrigin.status(), noKey.status()))
                .containsExactly(401, 403, 401);
        assertThat(List.of(noSession, wrongOrigin, noKey)).extracting(Reply::contentType)
                .allMatch(type -> type.startsWith("application/problem+json"));
    }

    @Test
    @Order(3)
    void loginWithMissingFieldsIsBadRequest() {
        ApiClient api = new ApiClient(port);
        assertThat(api.post("/v1/admin/session", map("password", "x")).status()).isEqualTo(400);
        assertThat(api.post("/v1/admin/session", map("email", ADMIN_EMAIL)).status()).isEqualTo(400);
        assertThat(api.post("/v1/admin/session", map()).status()).isEqualTo(400);
    }

    @Test
    @Order(4)
    void uploadOverTheLimitIsPayloadTooLarge() {
        ApiClient api = new ApiClient(port);
        Reply big = api.upload("/v1/admin/companies/" + companyA + "/documents", "grande.txt",
                new byte[(int) MAX_UPLOAD + 1000], "Cookie", adminCookie);
        assertThat(big.status()).isEqualTo(413);
        assertThat(big.contentType()).startsWith("application/problem+json");
    }

    @Test
    @Order(5)
    void deletedAuthorStillNamedInSearchHit() throws InterruptedException {
        ApiClient api = new ApiClient(port);
        Reply admin = api.post("/v1/admin/companies/" + companyA + "/members", map("firstName", "Carla", "lastName", "Autora",
                "email", "carla@ejemplo.test", "roles", List.of("COMPANY_ADMIN"), "password", ADMIN_PASSWORD),
                "Cookie", adminCookie);
        assertThat(admin.status()).isEqualTo(201);
        Reply login = api.post("/v1/admin/session", map("email", "carla@ejemplo.test", "password", ADMIN_PASSWORD));
        String authorCookie = login.setCookie().split(";")[0];
        Reply upload = api.upload("/v1/admin/companies/" + companyA + "/documents", "revision.txt",
                "La revisión de la caldera ficticia se hace cada octubre.".getBytes(StandardCharsets.UTF_8),
                "Cookie", authorCookie);
        assertThat(upload.status()).isEqualTo(201);
        awaitReady(api, upload.json().get("document").get("id").asString());

        assertThat(api.delete("/v1/admin/users/" + admin.json().get("member").get("userId").asString(), "Cookie",
                adminCookie)
                .status()).isEqualTo(204);

        Reply found = api.post("/v1/search", map("queries", List.of("revisión caldera octubre")),
                "Authorization", keyA);
        assertThat(found.json().get("noSource").asBoolean()).isFalse();
        assertThat(found.json().get("hits").get(0).get("authorName").asString()).isEqualTo("Carla Autora");
        assertThat(api.get("/v1/admin/me", "Cookie", authorCookie).status()).isEqualTo(401);
    }

    @Test
    @Order(6)
    void documentOfDeletedProjectStaysVisibleToWhoCouldSeeIt() throws InterruptedException {
        ApiClient api = new ApiClient(port);
        String project = createProject(api, "Obra vieja");
        String assignedKey = assignedWorkerKey(api, "obra-vieja@ejemplo.test", project);
        Reply upload = api.upload("/v1/admin/companies/" + companyA + "/documents?projectId=" + project,
                "obra.txt", "El andamio ficticio de la obra vieja se retira en junio.".getBytes(StandardCharsets.UTF_8),
                "Cookie", adminCookie);
        assertThat(upload.status()).isEqualTo(201);
        awaitReady(api, upload.json().get("document").get("id").asString());
        Reply before = api.post("/v1/search", map("queries", List.of("andamio obra junio")), "Authorization",
                assignedKey);
        assertThat(before.json().get("hits").get(0).get("projectId").asString()).isEqualTo(project);

        assertThat(api.delete("/v1/admin/companies/" + companyA + "/projects/" + project, "Cookie", adminCookie)
                .status()).isEqualTo(204);

        assertThat(found(api, assignedKey, "andamio obra junio")).isTrue();
        assertThat(found(api, keyA, "andamio obra junio")).isFalse();
        Reply filtered = api.post("/v1/search", map("queries", List.of("andamio obra junio"), "projectId", project),
                "Authorization", assignedKey);
        assertThat(filtered.status()).isEqualTo(404);
    }

    @Test
    @Order(7)
    void samePersonInTwoCompaniesSeesEachCompanyWithItsOwnKey() throws InterruptedException {
        ApiClient api = new ApiClient(port);
        Reply inA = addWorker(api, companyA, "doble@ejemplo.test");
        Reply inB = addWorker(api, companyB, "doble@ejemplo.test");
        assertThat(inA.json().get("alreadyExisted").asBoolean()).isFalse();
        assertThat(inB.json().get("alreadyExisted").asBoolean()).isTrue();
        String userId = inA.json().get("member").get("userId").asString();
        assertThat(inB.json().get("member").get("userId").asString()).isEqualTo(userId);
        String personalKeyA = issueKey(api, companyA, userId);
        String personalKeyB = issueKey(api, companyB, userId);
        Reply upload = api.upload("/v1/admin/companies/" + companyB + "/documents", "extintores.txt",
                "El extintor ficticio de la nave se revisa cada septiembre.".getBytes(StandardCharsets.UTF_8),
                "Cookie", adminCookie);
        assertThat(upload.status()).isEqualTo(201);
        awaitReady(api, upload.json().get("document").get("id").asString());

        assertThat(found(api, personalKeyA, "contrato mantenimiento ascensor")).isTrue();
        assertThat(found(api, personalKeyA, "extintor nave septiembre")).isFalse();
        assertThat(found(api, personalKeyB, "extintor nave septiembre")).isTrue();
        assertThat(found(api, personalKeyB, "contrato mantenimiento ascensor")).isFalse();
    }

    @Test
    @Order(8)
    void adminWhoAlsoWorksReviewsOwnCard() {
        ApiClient api = new ApiClient(port);
        Reply added = api.post("/v1/admin/companies/" + companyA + "/members", map("firstName", "Dora", "lastName", "Doble",
                "email", "dora@ejemplo.test", "roles", List.of("COMPANY_ADMIN", "WORKER"),
                "password", ADMIN_PASSWORD), "Cookie", adminCookie);
        assertThat(added.status()).isEqualTo(201);
        String userId = added.json().get("member").get("userId").asString();
        String ownKey = issueKey(api, companyA, userId);
        String ownCookie = api.post("/v1/admin/session", map("email", "dora@ejemplo.test", "password",
                ADMIN_PASSWORD)).setCookie().split(";")[0];

        Reply saved = api.post("/v1/cards", map("title", "Revisión de la caldera",
                "body", "Se revisa cada octubre."), "Authorization", ownKey);
        assertThat(saved.status()).isEqualTo(201);
        Reply review = api.post("/v1/admin/cards/" + saved.json().get("id").asString() + "/review",
                map("decision", "APPROVE"), "Cookie", ownCookie);

        assertThat(review.status()).isEqualTo(200);
        assertThat(review.json().get("authorId").asString()).isEqualTo(userId);
        assertThat(review.json().get("review").get("reviewerId").asString()).isEqualTo(userId);
    }

    @Test
    @Order(9)
    void removedAndReAddedPersonDoesNotGetOldKeysBack() {
        ApiClient api = new ApiClient(port);
        Reply added = addWorker(api, companyA, "vuelve@ejemplo.test");
        String userId = added.json().get("member").get("userId").asString();
        String oldKey = issueKey(api, companyA, userId);
        assertThat(api.get("/v1/me", "Authorization", oldKey).status()).isEqualTo(200);

        assertThat(api.delete("/v1/admin/companies/" + companyA + "/members/" + userId, "Cookie", adminCookie)
                .status()).isEqualTo(204);
        Reply again = addWorker(api, companyA, "vuelve@ejemplo.test");

        assertThat(again.json().get("alreadyExisted").asBoolean()).isTrue();
        assertThat(api.get("/v1/me", "Authorization", oldKey).status()).isEqualTo(401);
        assertThat(api.get("/v1/me", "Authorization", issueKey(api, companyA, userId)).status()).isEqualTo(200);
    }

    private String createProject(ApiClient api, String name) {
        Reply created = api.post("/v1/admin/companies/" + companyA + "/projects", map("name", name), "Cookie",
                adminCookie);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.json().get("restricted").asBoolean()).isTrue();
        return created.json().get("id").asString();
    }

    private String assignedWorkerKey(ApiClient api, String email, String project) {
        Reply added = api.post("/v1/admin/companies/" + companyA + "/members", map("firstName", "Asignado", "lastName", "Gil",
                "email", email, "roles", List.of("WORKER"), "projectIds", List.of(project)), "Cookie", adminCookie);
        assertThat(added.status()).isEqualTo(201);
        return issueKey(api, companyA, added.json().get("member").get("userId").asString());
    }

    @Test
    @Order(10)
    void projectIsFoundOnlyByAssignedWorkers() throws InterruptedException {
        ApiClient api = new ApiClient(port);
        String project = createProject(api, "Obra cerrada");
        String assigned = assignedWorkerKey(api, "asignado@ejemplo.test", project);
        String notAssigned = issueKey(api, companyA,
                addWorker(api, companyA, "sin-asignar@ejemplo.test").json().get("member").get("userId").asString());
        Reply upload = api.upload("/v1/admin/companies/" + companyA + "/documents?projectId=" + project,
                "cerrada.txt", "La grúa ficticia de la obra cerrada llega en agosto.".getBytes(StandardCharsets.UTF_8),
                "Cookie", adminCookie);
        assertThat(upload.status()).isEqualTo(201);
        String document = upload.json().get("document").get("id").asString();
        awaitReady(api, document);

        Reply hit = api.post("/v1/search", map("queries", List.of("grúa obra agosto")), "Authorization", assigned);
        assertThat(hit.json().get("hits").get(0).get("sourceId").asString()).isEqualTo(document);
        assertThat(verified(api, assigned, document)).isTrue();
        assertThat(api.get("/v1/documents/" + document + "/pages/1", "Authorization", assigned).status())
                .isEqualTo(200);

        assertThat(found(api, notAssigned, "grúa obra agosto")).isFalse();
        assertThat(api.get("/v1/documents/" + document + "/pages/1", "Authorization", notAssigned).status())
                .isEqualTo(404);
        assertThat(verified(api, notAssigned, document)).isFalse();
        assertThat(api.get("/v1/me", "Authorization", notAssigned).json().get("projects")).isEmpty();
        assertThat(api.get("/v1/me", "Authorization", assigned).json().get("projects")).hasSize(1);
    }

    @Test
    @Order(11)
    void spreadsheetSheetIsAPageSearchableAndCitable() throws Exception {
        ApiClient api = new ApiClient(port);
        Reply upload = api.upload("/v1/admin/companies/" + companyA + "/documents", "cuotas.xlsx", xlsx(), "Cookie",
                adminCookie);
        assertThat(upload.status()).isEqualTo(201);
        String document = upload.json().get("document").get("id").asString();
        awaitReady(api, document);

        Reply hit = api.post("/v1/search", map("queries", List.of("fundición norte")), "Authorization", keyA);
        assertThat(hit.json().get("hits").get(0).get("sourceId").asString()).isEqualTo(document);
        assertThat(hit.json().get("hits").get(0).get("pageNumber").asInt()).isEqualTo(2);
        assertThat(citation(api, keyA, document, 2)).isTrue();
        assertThat(citation(api, keyA, document, 1)).isFalse();
        assertThat(found(api, keyB, "fundición norte")).isFalse();
        assertThat(api.get("/v1/documents/" + document + "/pages/2", "Authorization", keyB).status()).isEqualTo(404);
        assertThat(citation(api, keyB, document, 2)).isFalse();
    }

    @Test
    @Order(12)
    void odsSheetIsSearchableAndCitable() throws Exception {
        ApiClient api = new ApiClient(port);
        Reply upload = api.upload("/v1/admin/companies/" + companyA + "/documents", "plan.ods", ods(), "Cookie",
                adminCookie);
        assertThat(upload.status()).isEqualTo(201);
        String document = upload.json().get("document").get("id").asString();
        awaitReady(api, document);

        Reply hit = api.post("/v1/search", map("queries", List.of("soldadura paneles")), "Authorization", keyA);
        assertThat(hit.json().get("hits").get(0).get("sourceId").asString()).isEqualTo(document);
        Reply cited = api.post("/v1/citations/verify", map("documentId", document, "pageNumber", 1,
                "quote", "Soldadura  paneles  septiembre"), "Authorization", keyA);
        assertThat(cited.json().get("verified").asBoolean()).isTrue();
        assertThat(found(api, keyB, "soldadura paneles")).isFalse();
    }

    private static byte[] ods() throws Exception {
        String t = "xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" "
                + "xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\"";
        String cell = "<table:table-cell><text:p>%s</text:p></table:table-cell>";
        String content = "<doc " + t + "><table:table table:name=\"Plan\"><table:table-row>"
                + cell.formatted("Soldadura") + cell.formatted("paneles") + cell.formatted("septiembre")
                + "</table:table-row></table:table></doc>";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("mimetype"));
            zip.write("application/vnd.oasis.opendocument.spreadsheet".getBytes(StandardCharsets.US_ASCII));
            zip.putNextEntry(new java.util.zip.ZipEntry("content.xml"));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private boolean citation(ApiClient api, String key, String document, int page) {
        Reply cited = api.post("/v1/citations/verify", map("documentId", document, "pageNumber", page,
                "quote", "Grúa ficticia  fundición norte  agosto"), "Authorization", key);
        assertThat(cited.status()).isEqualTo(200);
        return cited.json().get("verified").asBoolean();
    }

    private static byte[] xlsx() throws Exception {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook book = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            book.createSheet("Cuotas").createRow(0).createCell(0).setCellValue("Socio ficticio Ana");
            org.apache.poi.ss.usermodel.Row row = book.createSheet("Obras").createRow(0);
            String[] cells = {"Grúa ficticia", "fundición norte", "agosto"};
            for (int i = 0; i < cells.length; i++) {
                row.createCell(i).setCellValue(cells[i]);
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            book.write(out);
            return out.toByteArray();
        }
    }

    private boolean verified(ApiClient api, String key, String document) {
        Reply cited = api.post("/v1/citations/verify",
                map("documentId", document, "pageNumber", 1, "quote", "grúa ficticia de la obra cerrada"),
                "Authorization", key);
        assertThat(cited.status()).isEqualTo(200);
        return cited.json().get("verified").asBoolean();
    }

    private Reply addWorker(ApiClient api, String company, String email) {
        Reply added = api.post("/v1/admin/companies/" + company + "/members",
                map("firstName", "Doble", "lastName", "Persona", "email", email, "roles", List.of("WORKER")), "Cookie",
                adminCookie);
        assertThat(added.status()).isEqualTo(201);
        return added;
    }

    private boolean found(ApiClient api, String key, String query) {
        Reply found = api.post("/v1/search", map("queries", List.of(query)), "Authorization", key);
        assertThat(found.status()).isEqualTo(200);
        return !found.json().get("noSource").asBoolean();
    }

    private void setUpAndLogIn(ApiClient api) {
        Reply setup = api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo Ejemplo",
                "adminName", "Admin", "adminEmail", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
        assertThat(setup.status()).isEqualTo(201);
        Reply login = api.post("/v1/admin/session", map("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
        assertThat(login.status()).isEqualTo(200);
        adminCookie = login.setCookie().split(";")[0];
    }

    private String createCompany(ApiClient api, String name, String code) {
        Reply created = api.post("/v1/admin/companies", map("name", name, "code", code), "Cookie", adminCookie);
        assertThat(created.status()).isEqualTo(201);
        String id = created.json().get("id").asString();
        Reply capabilities = api.put("/v1/admin/companies/" + id + "/capabilities",
                map("capabilities", List.of("SEARCH_KNOWLEDGE", "SAVE_KNOWLEDGE_CARD")), "Cookie", adminCookie);
        assertThat(capabilities.status()).isEqualTo(200);
        return id;
    }

    private String workerKey(ApiClient api, String company) {
        Reply member = api.post("/v1/admin/companies/" + company + "/members",
                map("firstName", "Persona", "lastName", company.substring(0, 4), "roles", List.of("WORKER")), "Cookie",
                adminCookie);
        assertThat(member.status()).isEqualTo(201);
        return issueKey(api, company, member.json().get("member").get("userId").asString());
    }

    private String issueKey(ApiClient api, String company, String userId) {
        Reply key = api.post("/v1/admin/companies/" + company + "/members/" + userId + "/keys",
                map("label", "portatil"), "Cookie", adminCookie);
        assertThat(key.status()).isEqualTo(201);
        return "Bearer " + key.json().get("key").asString();
    }

    private void awaitReady(ApiClient api, String document) throws InterruptedException {
        String status = "";
        for (int i = 0; i < 60 && !"READY".equals(status); i++) {
            Thread.sleep(500);
            status = api.get("/v1/admin/documents/" + document, "Cookie", adminCookie).json().get("status")
                    .asString();
        }
        assertThat(status).isEqualTo("READY");
    }

    private void approveCard(ApiClient api) {
        Reply saved = api.post("/v1/cards", map("title", "Renovación del ascensor",
                "body", "Se renueva cada año el quince de marzo."), "Authorization", keyA);
        assertThat(saved.status()).isEqualTo(201);
        assertThat(saved.json().get("status").asString()).isEqualTo("PENDING");
        JsonNode pending = api.get("/v1/admin/companies/" + companyA + "/cards", "Cookie", adminCookie).json();
        assertThat(pending.get("cards")).hasSize(1);
        assertThat(pending.get("total").asInt()).isEqualTo(1);
        Reply review = api.post("/v1/admin/cards/" + saved.json().get("id").asString() + "/review",
                map("decision", "APPROVE"), "Cookie", adminCookie);
        assertThat(review.json().get("status").asString()).isEqualTo("APPROVED");
    }
}
