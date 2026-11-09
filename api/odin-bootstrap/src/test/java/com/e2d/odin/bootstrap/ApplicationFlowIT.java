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
        companyA = createCompany(api, "Empresa A");
        companyB = createCompany(api, "Empresa B");
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

    private void setUpAndLogIn(ApiClient api) {
        Reply setup = api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo Ejemplo",
                "adminName", "Admin", "adminEmail", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
        assertThat(setup.status()).isEqualTo(201);
        Reply login = api.post("/v1/admin/session", map("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
        assertThat(login.status()).isEqualTo(200);
        adminCookie = login.setCookie().split(";")[0];
    }

    private String createCompany(ApiClient api, String name) {
        Reply created = api.post("/v1/admin/companies", map("name", name), "Cookie", adminCookie);
        assertThat(created.status()).isEqualTo(201);
        String id = created.json().get("id").asString();
        Reply capabilities = api.put("/v1/admin/companies/" + id + "/capabilities",
                map("capabilities", List.of("SEARCH_KNOWLEDGE", "SAVE_KNOWLEDGE_CARD")), "Cookie", adminCookie);
        assertThat(capabilities.status()).isEqualTo(200);
        return id;
    }

    private String workerKey(ApiClient api, String company) {
        Reply user = api.post("/v1/admin/companies/" + company + "/users",
                map("displayName", "Persona " + company.substring(0, 4), "role", "WORKER"), "Cookie", adminCookie);
        assertThat(user.status()).isEqualTo(201);
        Reply key = api.post("/v1/admin/users/" + user.json().get("id").asString() + "/keys",
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
        assertThat(pending).hasSize(1);
        Reply review = api.post("/v1/admin/cards/" + saved.json().get("id").asString() + "/review",
                map("decision", "APPROVE"), "Cookie", adminCookie);
        assertThat(review.json().get("status").asString()).isEqualTo("APPROVED");
    }
}
