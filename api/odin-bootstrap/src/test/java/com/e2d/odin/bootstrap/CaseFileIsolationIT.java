package com.e2d.odin.bootstrap;

import static com.e2d.odin.bootstrap.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.bootstrap.ApiClient.Reply;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Un expediente solo lo ve y lo ordena quien ve su proyecto, y no se distingue «no existe» de «no lo ves». */
@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class CaseFileIsolationIT {

    private static final String SETUP_TOKEN = "setup-case-file-token";
    // base de datos propia: la instalación (setup) solo se hace una vez y ApplicationFlowIT comparte la de AbstractIT
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
        registry.add("ODIN_SETUP_TOKEN", () -> SETUP_TOKEN);
        registry.add("ODIN_ORIGINALS_DIR", () -> System.getProperty("java.io.tmpdir"));
        registry.add("ODIN_UPLOAD_MAX_BYTES", () -> "1000000");
    }

    private static final String ADMIN_EMAIL = "admin@ejemplo.test";
    private static final String ADMIN_PASSWORD = "clave-muy-larga-1";

    @LocalServerPort int port;

    private String adminCookie;
    private String company;

    @Test
    void workerWithoutTheProjectGets404AndCrossProjectAddIs404() throws InterruptedException {
        ApiClient api = new ApiClient(port);
        setUp(api);
        String projectA = project(api, "Obra A");
        String projectB = project(api, "Obra B");
        String marta = worker(api, "marta@ejemplo.test", List.of(projectA, projectB));
        String javier = worker(api, "javier@ejemplo.test", List.of());
        String caseId = upload(api, "ficha.txt", projectA, true);
        String inA = upload(api, "anexo-a.txt", projectA, false);
        String inB = upload(api, "anexo-b.txt", projectB, false);
        awaitReady(api, inA);
        String file = "/v1/cases/" + caseId + "/file";

        assertThat(api.post(file + "/documents", map("documentIds", List.of(inB)), "Authorization", marta).status())
                .isEqualTo(404);
        assertThat(api.post(file + "/documents", map("documentIds", List.of(inA)), "Authorization", marta).status())
                .isEqualTo(200);
        assertThat(api.get(file, "Authorization", marta).json().get("documents")).hasSize(1);

        assertThat(api.get(file, "Authorization", javier).status()).isEqualTo(404);
        assertThat(api.post(file + "/documents", map("documentIds", List.of(inA)), "Authorization", javier).status())
                .isEqualTo(404);
        assertThat(api.delete(file + "/documents/" + inA, "Authorization", javier).status()).isEqualTo(404);
        assertThat(api.get(file, "Authorization", marta).json().get("documents")).hasSize(1);
        assertThat(api.delete(file + "/documents/" + inA, "Authorization", marta).status()).isEqualTo(204);
    }

    private void setUp(ApiClient api) {
        assertThat(api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo Ejemplo", "adminName",
                "Admin", "adminEmail", ADMIN_EMAIL, "password", ADMIN_PASSWORD)).status()).isEqualTo(201);
        Reply login = api.post("/v1/admin/session", map("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
        adminCookie = login.setCookie().split(";")[0];
        Reply created = api.post("/v1/admin/companies", map("name", "Alfa Ejemplos", "code", "ALFA01"), "Cookie",
                adminCookie);
        company = created.json().get("id").asString();
        assertThat(api.put("/v1/admin/companies/" + company + "/capabilities",
                map("capabilities", List.of("SEARCH_KNOWLEDGE", "CASE_FILES")), "Cookie", adminCookie).status())
                .isEqualTo(200);
    }

    private String project(ApiClient api, String name) {
        return api.post("/v1/admin/companies/" + company + "/projects", map("name", name), "Cookie", adminCookie)
                .json().get("id").asString();
    }

    private String worker(ApiClient api, String email, List<String> projects) {
        Reply added = api.post("/v1/admin/companies/" + company + "/members", map("firstName", "Persona", "lastName",
                "Gil", "email", email, "roles", List.of("WORKER"), "projectIds", projects), "Cookie", adminCookie);
        assertThat(added.status()).isEqualTo(201);
        String userId = added.json().get("member").get("userId").asString();
        Reply key = api.post("/v1/admin/companies/" + company + "/members/" + userId + "/keys",
                map("label", "portatil"), "Cookie", adminCookie);
        return "Bearer " + key.json().get("key").asString();
    }

    private String upload(ApiClient api, String name, String project, boolean caseDocument) {
        Reply upload = api.upload("/v1/admin/companies/" + company + "/documents?projectId=" + project
                + "&caseDocument=" + caseDocument, name, ("Texto ficticio de " + name).getBytes(StandardCharsets.UTF_8),
                "Cookie", adminCookie);
        assertThat(upload.status()).isEqualTo(201);
        return upload.json().get("document").get("id").asString();
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
}
