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

/** Sin ODIN_PUBLIC_URL no se inventa ninguna dirección: se dice qué falta. */
@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class AccessLinkMissingUrlIT {

    private static final String SETUP_TOKEN = "setup-no-url-token";
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

    @LocalServerPort int port;

    @Test
    void missingPublicUrlMessage() {
        ApiClient api = new ApiClient(port);
        api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo Ejemplo", "adminName", "Admin",
                "adminEmail", "admin@ejemplo.test", "password", "clave-muy-larga-1"));
        String cookie = api.post("/v1/admin/session", map("email", "admin@ejemplo.test", "password",
                "clave-muy-larga-1")).setCookie().split(";")[0];
        String company = api.post("/v1/admin/companies", map("name", "Alfa", "code", "ALFA01"), "Cookie", cookie)
                .json().get("id").asString();
        api.put("/v1/admin/companies/" + company + "/capabilities",
                map("capabilities", List.of("SEARCH_KNOWLEDGE", "CASE_FILES")), "Cookie", cookie);
        String project = api.post("/v1/admin/companies/" + company + "/projects", map("name", "Obra"), "Cookie",
                cookie).json().get("id").asString();
        String bearer = worker(api, cookie, company, project);
        String caseId = api.upload("/v1/admin/companies/" + company + "/documents?projectId=" + project
                + "&caseDocument=true", "ficha.txt", "Ficha".getBytes(StandardCharsets.UTF_8), "Cookie", cookie)
                .json().get("document").get("id").asString();
        String docId = api.upload("/v1/admin/companies/" + company + "/documents?projectId=" + project, "a.txt",
                "Anexo".getBytes(StandardCharsets.UTF_8), "Cookie", cookie).json().get("document").get("id")
                .asString();

        Reply upload = api.post("/v1/cases/" + caseId + "/upload-links", map(), "Authorization", bearer);
        Reply original = api.post("/v1/documents/" + docId + "/original-links", map(), "Authorization", bearer);

        String message = "Falta configurar la dirección de Odín en la red local; avisa al administrador";
        assertThat(upload.status()).isEqualTo(400);
        assertThat(upload.json().get("detail").asString()).isEqualTo(message);
        assertThat(original.status()).isEqualTo(400);
        assertThat(original.json().get("detail").asString()).isEqualTo(message);
    }

    private static String worker(ApiClient api, String cookie, String company, String project) {
        Reply member = api.post("/v1/admin/companies/" + company + "/members", map("firstName", "Marta", "lastName",
                "Gil", "email", "marta@ejemplo.test", "roles", List.of("WORKER"), "projectIds", List.of(project)),
                "Cookie", cookie);
        String userId = member.json().get("member").get("userId").asString();
        return "Bearer " + api.post("/v1/admin/companies/" + company + "/members/" + userId + "/keys",
                map("label", "p"), "Cookie", cookie).json().get("key").asString();
    }
}
