package com.e2d.odin.bootstrap;

import static com.e2d.odin.bootstrap.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.bootstrap.ApiClient.Reply;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Retirar oculta el documento en todas las rutas del trabajador, con el rol de la aplicación, y recuperar lo devuelve. */
@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class WithdrawnDocumentIT {

    private static final String SETUP_TOKEN = "setup-withdrawn-token";
    // base de datos propia: la instalación (setup) solo se hace una vez
    private static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");
    private static final String ADMIN_EMAIL = "admin@ejemplo.test";
    private static final String ADMIN_PASSWORD = "clave-muy-larga-1";

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
    private static String company;
    private static String project;
    private static String worker;

    private ApiClient api;

    @BeforeEach
    void setUpOnce() {
        api = new ApiClient(port);
        if (company != null) {
            return;
        }
        assertThat(api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo Ejemplo", "adminName",
                "Admin", "adminEmail", ADMIN_EMAIL, "password", ADMIN_PASSWORD)).status()).isEqualTo(201);
        Reply login = api.post("/v1/admin/session", map("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
        adminCookie = login.setCookie().split(";")[0];
        company = api.post("/v1/admin/companies", map("name", "Alfa Ejemplos", "code", "ALFA01"), "Cookie",
                adminCookie).json().get("id").asString();
        assertThat(api.put("/v1/admin/companies/" + company + "/capabilities",
                map("capabilities", List.of("SEARCH_KNOWLEDGE", "SIMILAR_CASES", "CASE_FILES")), "Cookie",
                adminCookie).status()).isEqualTo(200);
        project = api.post("/v1/admin/companies/" + company + "/projects", map("name", "Obra A"), "Cookie",
                adminCookie).json().get("id").asString();
        worker = newWorker();
    }

    @Test
    void withdrawnDocumentVanishesFromEveryWorkerRouteAndComesBack() throws InterruptedException {
        String doc = upload("Texto ficticio zafiro cuadrante opalino", "zafiro.txt", true);
        awaitReady(doc);
        String quote = "zafiro cuadrante opalino";
        assertThat(visibleEverywhere(doc, quote)).isTrue();

        assertThat(api.put(withdrawal(doc), map(), "Cookie", adminCookie).status()).isEqualTo(200);
        assertThat(visibleEverywhere(doc, quote)).isFalse();
        assertThat(api.get("/v1/documents/" + doc + "/pages/1", "Authorization", worker).status()).isEqualTo(404);
        assertThat(verified(doc, quote)).isFalse();

        assertThat(api.delete(withdrawal(doc), "Cookie", adminCookie).status()).isEqualTo(200);
        assertThat(visibleEverywhere(doc, quote)).isTrue();
        assertThat(api.get("/v1/documents/" + doc + "/pages/1", "Authorization", worker).status()).isEqualTo(200);
    }

    @Test
    void withdrawnCaseHidesItsFile() throws InterruptedException {
        String caseId = upload("Texto ficticio de la ficha berilo", "ficha-berilo.txt", true);
        String member = upload("Texto ficticio del anexo berilo", "anexo-berilo.txt", false);
        awaitReady(caseId);
        awaitReady(member);
        String file = "/v1/cases/" + caseId + "/file";
        assertThat(api.post(file + "/documents", map("documentIds", List.of(member)), "Authorization", worker)
                .status()).isEqualTo(200);
        assertThat(api.get(file, "Authorization", worker).json().get("documents")).hasSize(1);

        assertThat(api.put(withdrawal(caseId), map(), "Cookie", adminCookie).status()).isEqualTo(200);
        assertThat(api.get(file, "Authorization", worker).status()).isEqualTo(404);

        assertThat(api.delete(withdrawal(caseId), "Cookie", adminCookie).status()).isEqualTo(200);
        assertThat(api.get(file, "Authorization", worker).json().get("documents")).hasSize(1);
    }

    @Test
    void adminListShowsWithdrawnOnlyWithFilter() throws InterruptedException {
        String doc = upload("Texto ficticio de la lista cuarzo", "lista-cuarzo.txt", false);
        String active = upload("Texto ficticio de la lista activa", "lista-activa.txt", false);
        awaitReady(doc);
        awaitReady(active);
        assertThat(api.put(withdrawal(doc), map(), "Cookie", adminCookie).status()).isEqualTo(200);
        String list = "/v1/admin/companies/" + company + "/documents";

        assertThat(ids(api.get(list, "Cookie", adminCookie))).doesNotContain(doc);
        Reply filtered = api.get(list + "?withdrawn=true", "Cookie", adminCookie);
        assertThat(ids(filtered)).contains(doc).doesNotContain(active);
        assertThat(filtered.json().get(ids(filtered).indexOf(doc)).get("withdrawnAt").isNull()).isFalse();
        assertThat(ids(api.get("/v1/documents", "Authorization", worker))).doesNotContain(doc);

        assertThat(api.delete(withdrawal(doc), "Cookie", adminCookie).status()).isEqualTo(200);
        assertThat(ids(api.get(list, "Cookie", adminCookie))).contains(doc);
    }

    @Test
    void restoringWhenAnEqualDocumentIsActiveConflicts() throws InterruptedException {
        String text = "Texto ficticio duplicado granate";
        String first = upload(text, "granate.txt", false);
        awaitReady(first);
        assertThat(api.put(withdrawal(first), map(), "Cookie", adminCookie).status()).isEqualTo(200);
        String second = upload(text, "granate-otra-vez.txt", false);
        assertThat(second).isNotEqualTo(first);
        awaitReady(second);

        Reply restore = api.delete(withdrawal(first), "Cookie", adminCookie);

        assertThat(restore.status()).isEqualTo(409);
        assertThat(restore.body()).contains("Ya hay un documento igual en ese proyecto");
        assertThat(api.put(withdrawal(second), map(), "Cookie", adminCookie).status()).isEqualTo(200);
        assertThat(api.delete(withdrawal(first), "Cookie", adminCookie).status()).isEqualTo(200);
    }

    @Test
    void workerCannotWithdrawAndUnknownDocumentIs404() {
        assertThat(api.put(withdrawal("00000000-0000-0000-0000-000000000001"), map(), "Cookie", adminCookie).status())
                .isEqualTo(404);
        assertThat(api.put(withdrawal("00000000-0000-0000-0000-000000000001"), map(), "Authorization", worker)
                .status()).isNotEqualTo(200);
    }

    private boolean visibleEverywhere(String doc, String quote) {
        boolean searched = api.post("/v1/search", map("queries", List.of(quote)), "Authorization", worker).json()
                .get("hits").findValues("sourceId").stream().anyMatch(n -> n.asString().equals(doc));
        boolean listed = ids(api.get("/v1/documents", "Authorization", worker)).contains(doc);
        boolean similar = api.post("/v1/similar-cases", map("queries", List.of(quote)), "Authorization", worker)
                .json().get("cases").findValues("sourceId").stream().anyMatch(n -> n.asString().equals(doc));
        boolean cited = verified(doc, quote);
        assertThat(List.of(searched, listed, similar, cited)).containsOnly(searched);
        return searched;
    }

    private boolean verified(String doc, String quote) {
        Reply cited = api.post("/v1/citations/verify", map("documentId", doc, "pageNumber", 1, "quote", quote),
                "Authorization", worker);
        return cited.status() == 200 && cited.json().get("verified").asBoolean();
    }

    private List<String> ids(Reply reply) {
        assertThat(reply.status()).isEqualTo(200);
        return reply.json().findValues("id").stream().map(n -> n.asString()).toList();
    }

    private static String withdrawal(String doc) {
        return "/v1/admin/documents/" + doc + "/withdrawal";
    }

    private String newWorker() {
        Reply added = api.post("/v1/admin/companies/" + company + "/members", map("firstName", "Persona", "lastName",
                "Gil", "email", "marta@ejemplo.test", "roles", List.of("WORKER"), "projectIds", List.of(project)),
                "Cookie", adminCookie);
        assertThat(added.status()).isEqualTo(201);
        String userId = added.json().get("member").get("userId").asString();
        Reply key = api.post("/v1/admin/companies/" + company + "/members/" + userId + "/keys",
                map("label", "portatil"), "Cookie", adminCookie);
        return "Bearer " + key.json().get("key").asString();
    }

    private String upload(String text, String name, boolean caseDocument) {
        Reply upload = api.upload("/v1/admin/companies/" + company + "/documents?projectId=" + project
                + "&caseDocument=" + caseDocument, name, text.getBytes(StandardCharsets.UTF_8), "Cookie",
                adminCookie);
        assertThat(upload.status()).isEqualTo(201);
        return upload.json().get("document").get("id").asString();
    }

    private void awaitReady(String document) throws InterruptedException {
        String status = "";
        for (int i = 0; i < 60 && !"READY".equals(status); i++) {
            Thread.sleep(500);
            status = api.get("/v1/admin/documents/" + document, "Cookie", adminCookie).json().get("status")
                    .asString();
        }
        assertThat(status).isEqualTo("READY");
    }
}
