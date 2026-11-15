package com.e2d.odin.bootstrap;

import static com.e2d.odin.bootstrap.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.e2d.odin.bootstrap.ApiClient.Reply;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Los enlaces de 15 minutos: quién los usa, desde dónde, cuántas veces y qué pueden leer. */
@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class AccessLinkIT {

    static final String PUBLIC_URL = "http://odin.test";
    private static final String SETUP_TOKEN = "setup-links-token";
    private static final String ADMIN_EMAIL = "admin@ejemplo.test";
    private static final String ADMIN_PASSWORD = "clave-muy-larga-1";
    private static final String OFFICE = "192.168.1.20";
    private static final String OTHER_DESK = "192.168.1.99";
    private static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static String adminCookie;
    private static String company;

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
        registry.add("ODIN_PUBLIC_URL", () -> PUBLIC_URL + "/");
        registry.add("ODIN_TRUSTED_PROXIES", () -> "127.0.0.1");
    }

    @LocalServerPort int port;

    private ApiClient api;
    private record Worker(String userId, String bearer, String keyId) {
    }

    @BeforeEach
    void connect() {
        api = new ApiClient("127.0.0.1", port);
        synchronized (AccessLinkIT.class) {
            if (adminCookie == null) {
                setUp();
            }
        }
    }

    private void setUp() {
        assertThat(api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo Ejemplo", "adminName",
                "Admin", "adminEmail", ADMIN_EMAIL, "password", ADMIN_PASSWORD)).status()).isEqualTo(201);
        Reply login = api.post("/v1/admin/session", map("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
        adminCookie = login.setCookie().split(";")[0];
        company = api.post("/v1/admin/companies", map("name", "Alfa Ejemplos", "code", "ALFA01"), "Cookie",
                adminCookie).json().get("id").asString();
        assertThat(api.put("/v1/admin/companies/" + company + "/capabilities",
                map("capabilities", List.of("SEARCH_KNOWLEDGE", "CASE_FILES")), "Cookie", adminCookie).status())
                .isEqualTo(200);
    }

    // ---- subida

    @Test
    void uploadLinkFlow() {
        String project = project("Obra del flujo");
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, "Ficha del caso").json().get("document").get("id")
                .asString();
        String token = tokenOf(requestUploadLink(marta, caseId));

        Reply info = api.get("/v1/upload-links/" + token, "X-Forwarded-For", OFFICE);
        assertThat(info.status()).isEqualTo(200);
        assertThat(info.json().get("projectName").asString()).isEqualTo("Obra del flujo");
        assertThat(info.json().get("caseTitle").asString()).isEqualTo("ficha.txt");
        assertThat(upload(token, "uno.txt", "primer anexo", OFFICE).json().get("filename").asString())
                .isEqualTo("uno.txt");
        assertThat(upload(token, "dos.txt", "segundo anexo", OFFICE).status()).isEqualTo(200);

        entries(caseId).forEach(this::awaitReady);
        var file = api.get("/v1/cases/" + caseId + "/file", "Authorization", marta.bearer()).json();
        assertThat(file.get("documents")).hasSize(2);
        file.get("documents").forEach(d -> {
            assertThat(d.get("projectId").asString()).isEqualTo(project);
            assertThat(d.get("uploadedBy").asString()).isEqualTo(marta.userId());
            assertThat(d.get("addedBy").asString()).isEqualTo(marta.userId());
            assertThat(d.get("uploadedByName").asString()).isEqualTo("Marta Gil");
            assertThat(d.get("addedByName").asString()).isEqualTo("Marta Gil");
        });
        var events = api.get("/v1/admin/companies/" + company + "/cases/" + caseId + "/events", "Cookie", adminCookie)
                .json();
        assertThat(events).hasSize(2);
        events.forEach(e -> assertThat(e.get("action").asString()).isEqualTo("ADDED"));
    }

    @Test
    void uploadLinkCannotReadAnything() {
        String project = project();
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, "Texto secreto de la ficha " + COUNTER.incrementAndGet()).json().get("document")
                .get("id").asString();
        String token = tokenOf(requestUploadLink(marta, caseId));
        upload(token, "uno.txt", "anexo reservado", OFFICE);

        List<Reply> replies = List.of(
                api.get("/v1/originals/" + token, "X-Forwarded-For", OFFICE),
                api.get("/v1/upload-links/" + token + "/documents", "X-Forwarded-For", OFFICE),
                api.get("/v1/upload-links/" + token + "/file", "X-Forwarded-For", OFFICE),
                api.get("/v1/cases/" + caseId + "/file", "Authorization", "Bearer " + token),
                api.get("/v1/documents", "Authorization", "Bearer " + token, "X-Forwarded-For", OFFICE));
        replies.forEach(r -> {
            assertThat(r.status()).isGreaterThanOrEqualTo(400);
            assertThat(r.body()).doesNotContain("Texto secreto", "anexo reservado", "ficha.txt");
        });
        assertThat(api.get("/v1/upload-links/" + token, "X-Forwarded-For", OFFICE).body())
                .doesNotContain("Texto secreto", "anexo reservado");
    }

    @Test
    void uploadCapHoldsUnderConcurrentUploads() throws Exception {
        String project = project();
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id").asString();
        String token = tokenOf(requestUploadLink(marta, caseId));
        ExecutorService pool = Executors.newFixedThreadPool(25);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            int n = i;
            results.add(pool.submit(() -> upload(token, "f" + n + ".txt", "contenido distinto " + n, OFFICE)
                    .status()));
        }
        int accepted = 0;
        for (Future<Integer> result : results) {
            accepted += result.get() == 200 ? 1 : 0;
        }
        pool.shutdown();

        assertThat(accepted).isEqualTo(20);
        assertThat(entries(caseId)).hasSize(20);
    }

    @Test
    void sameFileTwiceLinksOnce() {
        String project = project();
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id").asString();
        String token = tokenOf(requestUploadLink(marta, caseId));

        assertThat(upload(token, "a.txt", "idéntico", OFFICE).status()).isEqualTo(200);
        assertThat(upload(token, "b.txt", "idéntico", OFFICE).status()).isEqualTo(200);

        assertThat(entries(caseId)).hasSize(1);
        assertThat(count("SELECT count(*) FROM case_file_event WHERE case_document_id = '" + caseId + "'")).isOne();
    }

    @Test
    void duplicateInOtherProjectCreatesNewDocument() {
        String projectA = project();
        String projectB = project();
        Worker marta = worker(List.of(projectA));
        String caseId = adminUpload("ficha.txt", projectA, true, unique("Ficha")).json().get("document").get("id").asString();
        String hiddenElsewhere = adminUpload("ajeno.txt", projectB, false, "mismo contenido").json().get("document")
                .get("id").asString();
        String token = tokenOf(requestUploadLink(marta, caseId));

        Reply sent = upload(token, "propio.txt", "mismo contenido", OFFICE);

        assertThat(sent.status()).isEqualTo(200);
        assertThat(sent.body()).doesNotContain("otro proyecto", "existe");
        assertThat(sent.json().get("duplicate").asBoolean()).isFalse();
        assertThat(entries(caseId)).hasSize(1).doesNotContain(hiddenElsewhere);
        assertThat(strings("SELECT project_id FROM document WHERE id = '" + entries(caseId).get(0) + "'"))
                .containsExactly(projectA);
    }

    @Test
    void duplicateInCaseProjectOrGeneralIsLinked() {
        String project = project();
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id").asString();
        String inProject = adminUpload("anexo.txt", project, false, "contenido del proyecto").json().get("document")
                .get("id").asString();
        String general = adminUpload("general.txt", null, false, "contenido general").json().get("document").get("id")
                .asString();
        String token = tokenOf(requestUploadLink(marta, caseId));
        long before = count("SELECT count(*) FROM document");

        assertThat(upload(token, "x.txt", "contenido del proyecto", OFFICE).status()).isEqualTo(200);
        assertThat(upload(token, "y.txt", "contenido general", OFFICE).status()).isEqualTo(200);

        assertThat(count("SELECT count(*) FROM document")).isEqualTo(before);
        assertThat(entries(caseId)).containsExactlyInAnyOrder(inProject, general);
    }

    @Test
    void uploadLinkOfACaseWithoutProjectIsNotFound() {
        Worker marta = worker(List.of(project()));
        String caseId = adminUpload("ficha.txt", null, true, unique("Ficha")).json().get("document").get("id").asString();

        Reply reply = api.post("/v1/cases/" + caseId + "/upload-links", map(), "Authorization", marta.bearer());

        assertThat(reply.status()).isEqualTo(404);
    }

    @Test
    void expiredLinkIsNotFound() {
        String project = project();
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id").asString();
        String token = tokenOf(requestUploadLink(marta, caseId));
        assertThat(api.get("/v1/upload-links/" + token, "X-Forwarded-For", OFFICE).status()).isEqualTo(200);

        execute("UPDATE access_link SET expires_at = now() - interval '1 minute' WHERE user_id = '" + marta.userId()
                + "'");

        assertThat(api.get("/v1/upload-links/" + token, "X-Forwarded-For", OFFICE).status()).isEqualTo(404);
        assertThat(upload(token, "tarde.txt", "tarde", OFFICE).status()).isEqualTo(404);
    }

    // ---- dirección

    @Test
    void linkBoundToFirstAddress() {
        String project = project();
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id").asString();
        String token = tokenOf(requestUploadLink(marta, caseId));

        assertThat(api.get("/v1/upload-links/" + token, "X-Forwarded-For", OFFICE).status()).isEqualTo(200);

        assertThat(api.get("/v1/upload-links/" + token, "X-Forwarded-For", OTHER_DESK).status()).isEqualTo(404);
        assertThat(upload(token, "otro.txt", "otro", OTHER_DESK).status()).isEqualTo(404);
        assertThat(upload(token, "mio.txt", "mio", OFFICE).status()).isEqualTo(200);
    }

    @Test
    void boundLinkCannotBeSpoofedWithForwardedFor() {
        assumeTrue(ipv6LoopbackWorks(), "sin IPv6 en bucle no hay una segunda dirección que no sea proxy");
        String project = project();
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id").asString();
        String token = tokenOf(requestUploadLink(marta, caseId));
        assertThat(api.get("/v1/upload-links/" + token, "X-Forwarded-For", OFFICE).status()).isEqualTo(200);

        ApiClient notAProxy = new ApiClient("[::1]", port);

        assertThat(notAProxy.get("/v1/upload-links/" + token, "X-Forwarded-For", OFFICE).status()).isEqualTo(404);
        assertThat(api.get("/v1/upload-links/" + token, "X-Forwarded-For", OFFICE).status()).isEqualTo(200);
    }

    @Test
    void publicAddressRejected() {
        String project = project();
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id").asString();
        String token = tokenOf(requestUploadLink(marta, caseId));

        assertThat(api.get("/v1/upload-links/" + token, "X-Forwarded-For", "8.8.8.8").status()).isEqualTo(404);
        assertThat(upload(token, "a.txt", "a", "8.8.8.8").status()).isEqualTo(404);
        assertThat(count("SELECT count(*) FROM access_link WHERE bound_address IS NOT NULL AND user_id = '"
                + marta.userId() + "'")).isZero();
    }

    // ---- la persona

    @Test
    void linkStopsWhenMembershipRemoved() {
        String project = project();
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id").asString();
        String token = tokenOf(requestUploadLink(marta, caseId));
        assertThat(api.get("/v1/upload-links/" + token, "X-Forwarded-For", OFFICE).status()).isEqualTo(200);

        assertThat(api.delete("/v1/admin/companies/" + company + "/projects/" + project + "/members/"
                + marta.userId(), "Cookie", adminCookie).status()).isEqualTo(204);

        assertThat(upload(token, "a.txt", "a", OFFICE).status()).isEqualTo(404);
        assertThat(api.get("/v1/upload-links/" + token, "X-Forwarded-For", OFFICE).status()).isEqualTo(404);
    }

    @Test
    void linkStopsWhenKeyRevoked() {
        String project = project();
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id").asString();
        String token = tokenOf(requestUploadLink(marta, caseId));
        assertThat(api.get("/v1/upload-links/" + token, "X-Forwarded-For", OFFICE).status()).isEqualTo(200);

        assertThat(api.delete("/v1/admin/keys/" + marta.keyId(), "Cookie", adminCookie).status()).isEqualTo(204);

        assertThat(api.get("/v1/upload-links/" + token, "X-Forwarded-For", OFFICE).status()).isEqualTo(404);
        assertThat(count("SELECT count(*) FROM access_link WHERE user_id = '" + marta.userId() + "'")).isZero();
    }

    // ---- original

    private Reply openOriginalOf(Worker marta, String project, String name, byte[] content) {
        String id = adminUploadBytes(name, project, content).json().get("document").get("id").asString();
        Reply reply = api.get(path(requestOriginalLink(marta, id, 4)), "X-Forwarded-For", OFFICE);
        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.bytes()).isEqualTo(content);
        assertThat(reply.header("Cache-Control")).isEqualTo("no-store");
        assertThat(reply.header("X-Content-Type-Options")).isEqualTo("nosniff");
        return reply;
    }

    @Test
    void originalHeadersOfAPdfLetTheBrowserViewerRenderIt() {
        String project = project();
        Worker marta = worker(List.of(project));

        Reply pdf = openOriginalOf(marta, project, "informe.pdf", withSuffix("%PDF-1.4 ficticio".getBytes()));

        assertThat(pdf.contentType()).startsWith("application/pdf");
        assertThat(pdf.header("Content-Disposition")).startsWith("inline");
        assertThat(pdf.header("Content-Security-Policy")).isNull();
    }

    @Test
    void originalHeadersOfEveryOtherTypeKeepTheSandbox() throws Exception {
        String project = project();
        Worker marta = worker(List.of(project));

        Reply docx = openOriginalOf(marta, project, "acta.docx", docx(COUNTER.incrementAndGet()));
        Reply text = openOriginalOf(marta, project, "nota.txt", withSuffix("texto plano".getBytes()));
        Reply markdown = openOriginalOf(marta, project, "nota.md", withSuffix("# titulo\ncuerpo".getBytes()));
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, (byte) COUNTER.incrementAndGet()};
        Reply image = openOriginalOf(marta, project, "foto.png", png);

        assertThat(docx.header("Content-Disposition")).startsWith("attachment");
        assertThat(text.contentType()).startsWith("text/plain");
        assertThat(markdown.contentType()).startsWith("text/plain");
        assertThat(image.contentType()).startsWith("image/png");
        for (Reply reply : List.of(docx, text, markdown, image)) {
            assertThat(reply.header("Content-Security-Policy")).isEqualTo("sandbox");
        }
        assertThat(text.header("Content-Disposition")).startsWith("inline");
        assertThat(image.header("Content-Disposition")).startsWith("inline");
    }

    @Test
    void originalLinkCarriesThePageInTheFragment() {
        String project = project();
        Worker marta = worker(List.of(project));
        String id = adminUploadBytes("informe.pdf", project, withSuffix("%PDF-1.4 x".getBytes())).json()
                .get("document").get("id").asString();

        assertThat(requestOriginalLink(marta, id, 4)).endsWith("#page=4");
    }

    @Test
    void originalKeepsNonAsciiFilename() {
        String project = project();
        Worker marta = worker(List.of(project));
        String id = adminUploadBytes("Informe año ñ.pdf", project, withSuffix("%PDF-1.4 x".getBytes())).json().get("document")
                .get("id").asString();

        String disposition = api.get(path(requestOriginalLink(marta, id, null)), "X-Forwarded-For", OFFICE)
                .header("Content-Disposition");

        assertThat(disposition).contains("filename=\"Informe ano n.pdf\"")
                .contains("filename*=UTF-8''Informe%20a%C3%B1o%20%C3%B1.pdf");
    }

    @Test
    void originalOfWithdrawnIsNotFound() {
        String project = project();
        Worker marta = worker(List.of(project));
        String id = adminUploadBytes("informe.pdf", project, withSuffix("%PDF-1.4 retirado".getBytes())).json().get("document")
                .get("id").asString();
        String link = requestOriginalLink(marta, id, null);
        assertThat(api.get(path(link), "X-Forwarded-For", OFFICE).status()).isEqualTo(200);

        execute("UPDATE document SET withdrawn_at = now(), withdrawn_by = '" + marta.userId() + "' WHERE id = '" + id
                + "'");

        assertThat(api.get(path(link), "X-Forwarded-For", OFFICE).status()).isEqualTo(404);
        assertThat(api.post("/v1/documents/" + id + "/original-links", map(), "Authorization", marta.bearer())
                .status()).isEqualTo(404);
    }

    @Test
    void workerWithoutTheProjectCannotRequestLinks() {
        String project = project();
        Worker javier = worker(List.of());
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id").asString();
        String docId = adminUpload("anexo.txt", project, false, unique("Anexo")).json().get("document").get("id").asString();

        assertThat(api.post("/v1/cases/" + caseId + "/upload-links", map(), "Authorization", javier.bearer())
                .status()).isEqualTo(404);
        assertThat(api.post("/v1/documents/" + docId + "/original-links", map(), "Authorization", javier.bearer())
                .status()).isEqualTo(404);
    }

    @Test
    void administratorDownloadsTheOriginalWithTheSessionAndNoToken() {
        String project = project();
        byte[] pdf = withSuffix("%PDF-1.4 del administrador".getBytes());
        String id = adminUploadBytes("informe.pdf", project, pdf).json().get("document").get("id").asString();

        Reply reply = api.get("/v1/admin/documents/" + id + "/original", "Cookie", adminCookie);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.bytes()).isEqualTo(pdf);
        assertThat(reply.header("Cache-Control")).isEqualTo("no-store");
        assertThat(api.get("/v1/admin/documents/" + id + "/original").status()).isEqualTo(401);
    }

    @Test
    void administratorUploadWithCaseIdAddsToTheFile() {
        String project = project();
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id")
                .asString();

        Reply reply = api.upload("/v1/admin/companies/" + company + "/documents?projectId=" + project + "&caseId="
                + caseId, "anexo.txt", unique("Anexo").getBytes(StandardCharsets.UTF_8), "Cookie", adminCookie);

        assertThat(reply.status()).isEqualTo(201);
        assertThat(entries(caseId)).containsExactly(reply.json().get("document").get("id").asString());
    }

    @Test
    void administratorUploadOfAFileThatIsInAnotherProjectCreatesANewDocumentInTheCaseProject() {
        String caseProject = project();
        String elsewhere = project();
        String caseId = adminUpload("ficha.txt", caseProject, true, unique("Ficha")).json().get("document").get("id")
                .asString();
        String same = unique("mismo fichero");
        String other = adminUpload("ajeno.txt", elsewhere, false, same).json().get("document").get("id").asString();

        Reply reply = api.upload("/v1/admin/companies/" + company + "/documents?caseId=" + caseId, "propio.txt",
                same.getBytes(StandardCharsets.UTF_8), "Cookie", adminCookie);

        String created = reply.json().get("document").get("id").asString();
        assertThat(reply.status()).isEqualTo(201);
        assertThat(created).isNotEqualTo(other);
        assertThat(strings("SELECT project_id FROM document WHERE id = '" + created + "'")).containsExactly(caseProject);
        assertThat(entries(caseId)).containsExactly(created);
    }

    @Test
    void aCaseInADeletedProjectIsReadableButNotWritable() {
        String project = project();
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id")
                .asString();
        String docId = adminUpload("anexo.txt", project, false, unique("Anexo")).json().get("document").get("id")
                .asString();
        String token = tokenOf(requestUploadLink(marta, caseId));
        assertThat(api.delete("/v1/admin/companies/" + company + "/projects/" + project, "Cookie", adminCookie)
                .status()).isEqualTo(204);

        assertThat(api.get("/v1/cases/" + caseId + "/file", "Authorization", marta.bearer()).status()).isEqualTo(200);
        assertThat(api.post("/v1/cases/" + caseId + "/upload-links", map(), "Authorization", marta.bearer())
                .status()).isEqualTo(404);
        assertThat(upload(token, "a.txt", "a", OFFICE).status()).isEqualTo(404);
        assertThat(api.get("/v1/upload-links/" + token, "X-Forwarded-For", OFFICE).status()).isEqualTo(404);
        assertThat(api.post("/v1/cases/" + caseId + "/file/documents", map("documentIds", List.of(docId)),
                "Authorization", marta.bearer()).status()).isEqualTo(404);
    }

    @Test
    void administratorUploadWithAnUnusableCaseStoresNothing() {
        String project = project();
        String notACase = adminUpload("anexo.txt", project, false, unique("Anexo")).json().get("document").get("id")
                .asString();
        long before = count("SELECT count(*) FROM document");

        Reply reply = api.upload("/v1/admin/companies/" + company + "/documents?projectId=" + project + "&caseId="
                + notACase, "otro.txt", unique("Otro").getBytes(StandardCharsets.UTF_8), "Cookie", adminCookie);

        assertThat(reply.status()).isEqualTo(404);
        assertThat(count("SELECT count(*) FROM document")).isEqualTo(before);
    }

    @Test
    void administratorUploadIntoTheWrongProjectOfTheCaseStoresNothing() {
        String caseProject = project();
        String other = project();
        String caseId = adminUpload("ficha.txt", caseProject, true, unique("Ficha")).json().get("document").get("id")
                .asString();
        long before = count("SELECT count(*) FROM document");

        Reply reply = api.upload("/v1/admin/companies/" + company + "/documents?projectId=" + other + "&caseId="
                + caseId, "otro.txt", unique("Otro").getBytes(StandardCharsets.UTF_8), "Cookie", adminCookie);

        assertThat(reply.status()).isEqualTo(404);
        assertThat(count("SELECT count(*) FROM document")).isEqualTo(before);
    }

    @Test
    void parallelIdenticalUploadsBothSucceedAndLinkOneDocumentOnce() throws Exception {
        String project = project();
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id")
                .asString();
        String token = tokenOf(requestUploadLink(marta, caseId));
        String same = unique("contenido idéntico en paralelo");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (String name : List.of("a.txt", "b.txt")) {
            results.add(pool.submit(() -> upload(token, name, same, OFFICE).status()));
        }

        assertThat(results.get(0).get()).isEqualTo(200);
        assertThat(results.get(1).get()).isEqualTo(200);
        pool.shutdown();
        assertThat(entries(caseId)).hasSize(1);
        assertThat(count("SELECT count(*) FROM case_file_event WHERE case_document_id = '" + caseId + "'")).isOne();
    }

    @Test
    void aWithdrawnCopyOfTheSameFileDoesNotBlockTheUpload() {
        String project = project();
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id")
                .asString();
        String text = unique("contenido retirado");
        String old = adminUpload("viejo.txt", project, false, text).json().get("document").get("id").asString();
        assertThat(api.put("/v1/admin/documents/" + old + "/withdrawal", map(), "Cookie", adminCookie).status())
                .isEqualTo(200);
        String token = tokenOf(requestUploadLink(marta, caseId));

        Reply sent = upload(token, "nuevo.txt", text, OFFICE);

        assertThat(sent.status()).isEqualTo(200);
        assertThat(sent.body()).doesNotContain("retirado");
        assertThat(sent.json().get("duplicate").asBoolean()).isFalse();
        assertThat(entries(caseId)).hasSize(1).doesNotContain(old);
    }

    @Test
    void tokenNotStoredInClear() {
        String project = project();
        Worker marta = worker(List.of(project));
        String caseId = adminUpload("ficha.txt", project, true, unique("Ficha")).json().get("document").get("id").asString();
        String token = tokenOf(requestUploadLink(marta, caseId));

        List<String> stored = strings("SELECT token_hash FROM access_link WHERE user_id = '" + marta.userId() + "'");

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0)).doesNotContain(token).isNotEqualTo(token);
        assertThat(token.length()).isGreaterThanOrEqualTo(43);
    }

    // ---- ayudas

    private String project() {
        return project("Obra " + COUNTER.incrementAndGet());
    }

    private String project(String name) {
        return api.post("/v1/admin/companies/" + company + "/projects", map("name", name), "Cookie", adminCookie)
                .json().get("id").asString();
    }

    private Worker worker(List<String> projects) {
        String email = "persona" + COUNTER.incrementAndGet() + "@ejemplo.test";
        Reply added = api.post("/v1/admin/companies/" + company + "/members", map("firstName", "Marta", "lastName",
                "Gil", "email", email, "roles", List.of("WORKER"), "projectIds", projects), "Cookie", adminCookie);
        assertThat(added.status()).isEqualTo(201);
        String userId = added.json().get("member").get("userId").asString();
        Reply key = api.post("/v1/admin/companies/" + company + "/members/" + userId + "/keys",
                map("label", "portatil"), "Cookie", adminCookie);
        return new Worker(userId, "Bearer " + key.json().get("key").asString(), key.json().get("id").asString());
    }

    private static String unique(String text) {
        return text + " " + COUNTER.incrementAndGet();
    }

    private Reply adminUpload(String name, String project, boolean caseDocument, String text) {
        String query = (project == null ? "" : "projectId=" + project + "&") + "caseDocument=" + caseDocument;
        Reply reply = api.upload("/v1/admin/companies/" + company + "/documents?" + query, name,
                text.getBytes(StandardCharsets.UTF_8), "Cookie", adminCookie);
        assertThat(reply.status()).isEqualTo(201);
        return reply;
    }

    private Reply adminUploadBytes(String name, String project, byte[] content) {
        Reply reply = api.upload("/v1/admin/companies/" + company + "/documents?projectId=" + project, name, content,
                "Cookie", adminCookie);
        assertThat(reply.status()).isEqualTo(201);
        return reply;
    }

    private String requestUploadLink(Worker worker, String caseId) {
        Reply reply = api.post("/v1/cases/" + caseId + "/upload-links", map(), "Authorization", worker.bearer());
        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.json().get("expiresAt").asString()).isNotBlank();
        return reply.json().get("url").asString();
    }

    private String requestOriginalLink(Worker worker, String documentId, Integer page) {
        Reply reply = api.post("/v1/documents/" + documentId + "/original-links",
                page == null ? map() : map("page", page), "Authorization", worker.bearer());
        assertThat(reply.status()).isEqualTo(201);
        return reply.json().get("url").asString();
    }

    private static String tokenOf(String url) {
        assertThat(url).startsWith(PUBLIC_URL + "/subir/");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    /** La parte de la URL que llega al servidor: sin dirección pública y sin fragmento. */
    private static String path(String url) {
        assertThat(url).startsWith(PUBLIC_URL + "/v1/originals/");
        return url.substring(PUBLIC_URL.length()).split("#")[0];
    }

    private Reply upload(String token, String name, String text, String from) {
        return api.upload("/v1/upload-links/" + token + "/documents", name, text.getBytes(StandardCharsets.UTF_8),
                "X-Forwarded-For", from);
    }

    private static byte[] withSuffix(byte[] content) {
        return (new String(content, StandardCharsets.UTF_8) + " " + COUNTER.incrementAndGet())
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] docx(int salt) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes());
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<w:document>" + salt + "</w:document>").getBytes());
        }
        return out.toByteArray();
    }

    private static boolean ipv6LoopbackWorks() {
        try (var socket = new java.net.ServerSocket(0, 1, InetAddress.getByName("::1"))) {
            return socket.isBound();
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> entries(String caseId) {
        return strings("SELECT document_id FROM case_file_entry WHERE case_document_id = '" + caseId + "'");
    }

    private void awaitReady(String documentId) {
        String status = "";
        for (int i = 0; i < 60 && !"READY".equals(status); i++) {
            status = api.get("/v1/admin/documents/" + documentId, "Cookie", adminCookie).json().get("status")
                    .asString();
            if (!"READY".equals(status)) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        assertThat(status).isEqualTo("READY");
    }

    private static long count(String sql) {
        return Long.parseLong(strings(sql).get(0));
    }

    private static List<String> strings(String sql) {
        try (Connection c = owner(); var st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            List<String> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(rs.getString(1));
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void execute(String sql) {
        try (Connection c = owner(); var st = c.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    }
}
