package com.e2d.odin.bootstrap;

import static com.e2d.odin.bootstrap.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.bootstrap.ApiClient.Reply;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

/** Versiones y correcciones en todas las salidas que dan contenido de documentos, de extremo a extremo y sin IA. */
@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class ContextOutputsIT {

    private static final String SETUP_TOKEN = "setup-context-outputs";
    private static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");
    private static final String ADMIN_EMAIL = "admin@ejemplo.test";
    private static final String ADMIN_PASSWORD = "clave-muy-larga-1";
    private static final String DEDUCTIBLE = "franquicia por daños por agua";

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

    private ApiClient api;
    private String adminCookie;
    private String company;
    private String marta;
    private final List<String> policies = new ArrayList<>();
    private String invoice;
    private String correction;
    private String hiddenInvoice;
    private String hiddenCorrection;
    private String annex;
    private String caseFile;
    private String beta;

    @Test
    void everyOutputCarriesVersionsCorrectionsAndTheIncompleteFlag() throws Exception {
        api = new ApiClient(port);
        setUp();
        upload();
        for (int i = 0; i < 3; i++) {
            assertThat(api.put("/v1/admin/documents/" + policies.get(i) + "/superseded-by",
                    map("replacementId", policies.get(i + 1)), "Cookie", adminCookie).status()).isEqualTo(200);
        }
        eventually(() -> hit(search("ZZQ-2025-0302", 10), correction).get("corrections").size() == 1
                && hit(search("anexo ZZQ-99-00000", 10), annex).get("incomplete").asBoolean());

        assertSearch();
        assertReadPage();
        assertCaseFile();
        assertVerifyAndList();
        assertSimilarCases();
        assertOtherCompany();
    }

    private void assertSearch() throws IOException {
        JsonNode hit = assertChains();
        JsonNode original = assertCorrections();
        assertSizes(hit, original);
        assertHidden();
        assertThat(hit(search("anexo ZZQ-99-00000", 10), annex).get("incomplete").asBoolean()).isTrue();
        assertGroupingKeepsTheLimit();
    }

    private JsonNode assertChains() {
        JsonNode current = search(DEDUCTIBLE, 10);
        JsonNode hit = hit(current, policies.get(3));
        assertThat(titles(hit.get("versions"))).hasSize(4);
        assertThat(ids(hit.get("versions"))).containsExactlyElementsOf(policies);
        assertThat(hit.get("versions").get(3).get("current").asBoolean()).isTrue();
        assertThat(hit.get("versions").get(3).get("link").get("quote").asString())
                .contains("sustituye y anula a la póliza n.º ZZQ-22-77001");
        assertThat(hit.get("versions").get(3).get("heading").get("page").asInt()).isEqualTo(1);
        assertThat(hit.get("versions").get(0).get("heading").get("text").asString()).contains("Efecto: 1 de octubre de 2016");
        hit.get("versions").forEach(v -> assertThat(v.get("match").get("page").asInt()).isEqualTo(1));
        assertThat(hit.get("incomplete").asBoolean()).isFalse();
        assertThat(ids(current.get("hits"), "sourceId").stream().filter(policies::contains)).hasSize(1);

        JsonNode old = search("póliza ZZQ-16-77001", 10);
        assertThat(ids(old.get("hits"), "sourceId").stream().filter(policies::contains)).hasSize(1);
        String single = ids(old.get("hits"), "sourceId").stream().filter(policies::contains).findFirst().orElseThrow();
        assertThat(ids(hit(old, single).get("versions"))).containsExactlyElementsOf(policies);
        return hit;
    }

    private JsonNode assertCorrections() {
        JsonNode original = hit(search("ZZQ-2025-0302", 10), invoice);
        JsonNode by = original.get("corrections").get(0);
        assertThat(by.get("direction").asString()).isEqualTo("correctedBy");
        assertThat(by.get("relation").asString()).isEqualTo("RECTIFIES");
        assertThat(by.get("documentId").asString()).isEqualTo(correction);
        assertThat(by.get("confirmed").asBoolean()).isFalse();
        assertThat(by.get("evidencePage").asInt()).isEqualTo(1);
        assertThat(by.get("evidenceQuote").asString()).contains("Rectifica la factura ZZQ-2025-0302");
        assertThat(hit(search("ZZQ-2025-0302", 10), correction).get("corrections").get(0).get("direction")
                .asString()).isEqualTo("corrects");
        return original;
    }

    private void assertHidden() {
        JsonNode hit = hit(search("ZZQ-2026-0900", 10), hiddenInvoice);
        JsonNode hidden = hit.get("corrections").get(0);
        assertThat(hidden.get("hidden").asBoolean()).isTrue();
        assertThat(nullish(hidden.get("documentId"))).isTrue();
        assertThat(nullish(hidden.get("title"))).isTrue();
        assertThat(nullish(hidden.get("evidenceQuote"))).isTrue();
        assertThat(hit.get("incomplete").asBoolean()).isTrue();
        assertThat(search("ZZQ-2026-0900", 10).toString()).doesNotContain(hiddenCorrection).doesNotContain("secreta");
    }

    private void assertGroupingKeepsTheLimit() {
        JsonNode five = search(DEDUCTIBLE, 5);
        assertThat(five.get("hits")).hasSize(5);
        assertThat(ids(five.get("hits"), "sourceId").stream().filter(policies::contains)).hasSize(1);
    }

    private void assertSizes(JsonNode current, JsonNode original) throws IOException {
        String sizes = "vigente con 4 versiones: " + current.toString().getBytes(StandardCharsets.UTF_8).length
                + " bytes; original con su corrección: " + original.toString().getBytes(StandardCharsets.UTF_8).length
                + " bytes\n" + current + "\n" + original + "\n";
        Files.writeString(Path.of("target", "e30-sizes.txt"), sizes);
    }

    private void assertReadPage() {
        JsonNode page = api.get("/v1/documents/" + policies.get(3) + "/pages/1", "Authorization", marta).json();
        assertThat(ids(page.get("versions"))).containsExactlyElementsOf(policies);
        assertThat(nullish(page.get("versions").get(0).get("match"))).isTrue();
        assertThat(page.get("incomplete").asBoolean()).isFalse();
        JsonNode rectified = api.get("/v1/documents/" + correction + "/pages/1", "Authorization", marta).json();
        assertThat(rectified.get("corrections").get(0).get("direction").asString()).isEqualTo("corrects");
    }

    private void assertCaseFile() {
        String file = "/v1/cases/" + caseFile + "/file";
        JsonNode view = api.get(file, "Authorization", marta).json();
        JsonNode old = byId(view.get("documents"), policies.get(0));
        assertThat(old.get("superseded").asBoolean()).isTrue();
        assertThat(old.get("replacement").get("documentId").asString()).isEqualTo(policies.get(3));
        assertThat(byId(view.get("documents"), invoice).get("corrections").get(0).get("direction").asString())
                .isEqualTo("correctedBy");
        assertThat(view.get("documents").get(0).has("incomplete")).isTrue();
        JsonNode text = api.get(file + "/text", "Authorization", marta).json();
        JsonNode oldInText = byDocument(text.get("documents"), policies.get(0));
        assertThat(oldInText.get("superseded").asBoolean()).isTrue();
        assertThat(oldInText.get("replacement").get("documentId").asString()).isEqualTo(policies.get(3));
        assertThat(byDocument(text.get("documents"), invoice).get("corrections")).hasSize(1);
    }

    private void assertVerifyAndList() {
        JsonNode verified = api.post("/v1/citations/verify-batch", map("citations", List.of(
                map("documentId", policies.get(0), "pageNumber", 1, "quote", "Efecto: 1 de octubre de 2016"),
                map("documentId", invoice, "pageNumber", 1, "quote", "Total 481,58 euros"))), "Authorization", marta)
                .json().get("results");
        assertThat(verified.get(0).get("verified").asBoolean()).isTrue();
        assertThat(verified.get(0).get("superseded").asBoolean()).isTrue();
        assertThat(verified.get(0).get("replacement").get("documentId").asString()).isEqualTo(policies.get(3));
        assertThat(verified.get(0).get("corrected").asBoolean()).isFalse();
        assertThat(verified.get(1).get("corrected").asBoolean()).isTrue();
        assertThat(verified.get(1).get("corrections")).hasSize(1);
        JsonNode listed = api.get("/v1/documents", "Authorization", marta).json();
        JsonNode listedInvoice = byId(listed, invoice);
        assertThat(listedInvoice.get("corrected").asBoolean()).isTrue();
        assertThat(listedInvoice.get("correctionCount").asInt()).isEqualTo(1);
        assertThat(byId(listed, policies.get(3)).get("corrected").asBoolean()).isFalse();
    }

    private void assertSimilarCases() {
        JsonNode cases = api.post("/v1/similar-cases", map("queries", List.of("ficha de caso ZZQ-2025-0302")),
                "Authorization", marta).json().get("cases");
        JsonNode found = byField(cases, "sourceId", caseFile);
        assertThat(found.get("corrections")).isNotNull();
    }

    private void assertOtherCompany() {
        JsonNode hits = api.post("/v1/search", map("queries", List.of("ZZQ-25-77001 franquicia")), "Authorization",
                beta).json().get("hits");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).get("versions")).isEmpty();
        assertThat(hits.get(0).get("corrections")).isEmpty();
        assertThat(hits.toString()).doesNotContain(policies.get(3)).doesNotContain(invoice);
    }

    private void upload() throws InterruptedException {
        String projectB = project("Obra cerrada");
        marta = worker(company, "marta@ejemplo.test", List.of(project("Obra A")));
        uploadPolicies();
        uploadInvoices(projectB);
        String betaCompany = createCompany("Beta Ejemplos", "BETA01");
        beta = worker(betaCompany, "ana@ejemplo.test", List.of());
        upload(betaCompany, "Beta póliza.txt", null, false,
                "Póliza n.º ZZQ-25-77001\nLa " + DEDUCTIBLE + " de Beta es de 90 euros.");
        waitUntilAllReady();
        for (String id : List.of(policies.get(0), invoice)) {
            assertThat(api.post("/v1/cases/" + caseFile + "/file/documents", map("documentIds", List.of(id)),
                    "Authorization", marta).status()).isEqualTo(200);
        }
    }

    private void uploadPolicies() {
        for (int i = 0; i < 4; i++) {
            int year = 16 + 3 * i;
            String replaces = i == 0 ? "" : "Esta póliza sustituye y anula a la póliza n.º ZZQ-" + (year - 3)
                    + "-77001 desde el 1 de octubre de 20" + year + ".\n";
            policies.add(upload(company, "Póliza ZZQ-" + year + ".txt", null, false, "Póliza n.º ZZQ-" + year
                    + "-77001\nEfecto: 1 de octubre de 20" + year + "\n" + replaces + "La " + DEDUCTIBLE + " es de "
                    + (150 + 150 * i) + " euros."));
        }
    }

    private void uploadInvoices(String projectB) {
        invoice = upload(company, "Factura ZZQ-2025-0302.txt", null, false,
                "Número de factura: ZZQ-2025-0302\nFecha: 10/03/2025\nTotal 481,58 euros");
        correction = upload(company, "Rectificativa ZZQ-2025-0302-R.txt", null, false,
                "FACTURA RECTIFICATIVA\nNúmero de factura rectificativa: ZZQ-2025-0302-R\n"
                        + "Rectifica la factura ZZQ-2025-0302: importe correcto 389,00 euros.");
        hiddenInvoice = upload(company, "Factura ZZQ-2026-0900.txt", null, false,
                "Número de factura: ZZQ-2026-0900\nTotal 100,00 euros");
        hiddenCorrection = upload(company, "Rectificativa secreta.txt", projectB, false,
                "Número de factura rectificativa: ZZQ-2026-0900-R\nRectifica la factura ZZQ-2026-0900: importe secreta.");
        annex = upload(company, "Anexo.txt", null, false, "Anexo.\nEste anexo modifica la póliza n.º ZZQ-99-00000.");
        caseFile = upload(company, "Ficha de caso.txt", null, true,
                "Ficha de caso sobre la factura ZZQ-2025-0302 y las pólizas.");
        for (int i = 0; i < 6; i++) {
            // largos a propósito: el orden normaliza por longitud y los de relleno no deben tapar a las pólizas
            upload(company, "Relleno " + i + ".txt", null, false,
                    "Apunte " + i + " sobre la " + DEDUCTIBLE + ". " + "Texto de relleno sin más. ".repeat(40));
        }
    }

    private final List<String> uploaded = new ArrayList<>();

    private void waitUntilAllReady() throws InterruptedException {
        for (String id : uploaded) {
            String status = "";
            for (int i = 0; i < 80 && !"READY".equals(status); i++) {
                Thread.sleep(250);
                status = api.get("/v1/admin/documents/" + id, "Cookie", adminCookie).json().get("status").asString();
            }
            assertThat(status).as("documento " + id).isEqualTo("READY");
        }
    }

    private static void eventually(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 60 && !condition.getAsBoolean(); i++) {
            Thread.sleep(500);
        }
        assertThat(condition.getAsBoolean()).as("los vínculos se calculan en un carril aparte").isTrue();
    }

    private JsonNode search(String query, int limit) {
        return api.post("/v1/search", map("queries", List.of(query), "limit", limit), "Authorization", marta).json();
    }

    private static JsonNode hit(JsonNode response, String id) {
        return byField(response.get("hits"), "sourceId", id);
    }

    private static JsonNode byId(JsonNode array, String id) {
        return byField(array, "id", id);
    }

    private static JsonNode byDocument(JsonNode array, String id) {
        return byField(array, "documentId", id);
    }

    private static JsonNode byField(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.get(field).asString())) {
                return node;
            }
        }
        throw new AssertionError("No sale " + field + "=" + value + " en " + array);
    }

    private static List<String> ids(JsonNode versions) {
        return ids(versions, "documentId");
    }

    private static List<String> ids(JsonNode array, String field) {
        List<String> ids = new ArrayList<>();
        array.forEach(v -> ids.add(v.get(field).asString()));
        return ids;
    }

    private static List<String> titles(JsonNode versions) {
        return ids(versions, "title");
    }

    private static boolean nullish(JsonNode node) {
        return node == null || node.isNull();
    }

    private void setUp() {
        assertThat(api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo Ejemplo", "adminName",
                "Admin", "adminEmail", ADMIN_EMAIL, "password", ADMIN_PASSWORD)).status()).isEqualTo(201);
        Reply login = api.post("/v1/admin/session", map("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
        adminCookie = login.setCookie().split(";")[0];
        company = createCompany("Alfa Ejemplos", "ALFA01");
    }

    private String createCompany(String name, String code) {
        String id = api.post("/v1/admin/companies", map("name", name, "code", code), "Cookie", adminCookie).json()
                .get("id").asString();
        assertThat(api.put("/v1/admin/companies/" + id + "/capabilities",
                map("capabilities", List.of("SEARCH_KNOWLEDGE", "CASE_FILES", "SIMILAR_CASES")), "Cookie", adminCookie)
                .status()).isEqualTo(200);
        return id;
    }

    private String project(String name) {
        return api.post("/v1/admin/companies/" + company + "/projects", map("name", name), "Cookie", adminCookie)
                .json().get("id").asString();
    }

    private String worker(String of, String email, List<String> projects) {
        Reply added = api.post("/v1/admin/companies/" + of + "/members", map("firstName", "Persona", "lastName",
                "Gil", "email", email, "roles", List.of("WORKER"), "projectIds", projects), "Cookie", adminCookie);
        assertThat(added.status()).isEqualTo(201);
        String userId = added.json().get("member").get("userId").asString();
        return "Bearer " + api.post("/v1/admin/companies/" + of + "/members/" + userId + "/keys",
                map("label", "portatil"), "Cookie", adminCookie).json().get("key").asString();
    }

    private String upload(String of, String name, String project, boolean caseDocument, String text) {
        String query = (project == null ? "" : "projectId=" + project + "&") + "caseDocument=" + caseDocument;
        Reply upload = api.upload("/v1/admin/companies/" + of + "/documents?" + query, name,
                text.getBytes(StandardCharsets.UTF_8), "Cookie", adminCookie);
        assertThat(upload.status()).isEqualTo(201);
        String id = upload.json().get("document").get("id").asString();
        uploaded.add(id);
        return id;
    }
}
