package com.e2d.odin.bootstrap;

import static com.e2d.odin.bootstrap.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.link.DescribeUploadLink;
import com.e2d.odin.application.link.IssueUploadLink;
import com.e2d.odin.application.link.IssuedLink;
import com.e2d.odin.application.link.UploadByLink;
import com.e2d.odin.application.link.UploadReceipt;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Subir a un proyecto por enlace contra la base real: la carrera del índice único y el aislamiento entre proyectos. */
@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class ProjectUploadLinkIT {

    private static final String SETUP_TOKEN = "setup-project-upload-token";
    private static final String ADMIN_EMAIL = "admin@ejemplo.test";
    private static final String ADMIN_PASSWORD = "clave-muy-larga-1";
    private static final String OFFICE = "192.168.1.20";
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
        registry.add("ODIN_PUBLIC_URL", () -> "http://odin.test/");
        registry.add("ODIN_TRUSTED_PROXIES", () -> "127.0.0.1");
    }

    @LocalServerPort int port;
    @Autowired IssueUploadLink issueUploadLink;
    @Autowired UploadByLink uploadByLink;
    @Autowired DescribeUploadLink describeUploadLink;

    private ApiClient api;

    @BeforeEach
    void connect() {
        api = new ApiClient("127.0.0.1", port);
        synchronized (ProjectUploadLinkIT.class) {
            if (adminCookie == null) {
                setUp();
            }
        }
    }

    private void setUp() {
        assertThat(api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo Ejemplo", "adminName",
                "Admin", "adminEmail", ADMIN_EMAIL, "password", ADMIN_PASSWORD)).status()).isEqualTo(201);
        adminCookie = api.post("/v1/admin/session", map("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD)).setCookie()
                .split(";")[0];
        company = api.post("/v1/admin/companies", map("name", "Alfa Ejemplos", "code", "ALFA01"), "Cookie",
                adminCookie).json().get("id").asString();
        assertThat(api.put("/v1/admin/companies/" + company + "/capabilities",
                map("capabilities", List.of("SEARCH_KNOWLEDGE", "UPLOAD_DOCUMENTS")), "Cookie", adminCookie)
                .status()).isEqualTo(200);
    }

    private String project() {
        return api.post("/v1/admin/companies/" + company + "/projects", map("name", "Obra " + COUNTER.incrementAndGet()),
                "Cookie", adminCookie).json().get("id").asString();
    }

    private Actor worker(List<String> projects) {
        String email = "persona" + COUNTER.incrementAndGet() + "@ejemplo.test";
        var added = api.post("/v1/admin/companies/" + company + "/members", map("firstName", "Marta", "lastName", "Gil",
                "email", email, "roles", List.of("WORKER"), "projectIds", projects), "Cookie", adminCookie);
        assertThat(added.status()).isEqualTo(201);
        UserId id = new UserId(UUID.fromString(added.json().get("member").get("userId").asString()));
        return new Actor(id, "Marta", Role.WORKER, Set.of(new CompanyId(UUID.fromString(company))));
    }

    private String tokenOf(IssuedLink link) {
        return link.url().substring(link.url().lastIndexOf('/') + 1);
    }

    private UploadReceipt send(String token, String name, String text) {
        return uploadByLink.execute(token, OFFICE, name, text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void twoParallelUploadsOfTheSameFileGiveOneDocumentAndOneSaysItWasThere() throws Exception {
        String project = project();
        Actor marta = worker(List.of(project));
        String token = tokenOf(issueUploadLink.forProject(marta, new ProjectId(UUID.fromString(project))));
        String same = "contenido idéntico en paralelo " + COUNTER.incrementAndGet();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<UploadReceipt>> results = new ArrayList<>();
        for (String name : List.of("a.txt", "b.txt")) {
            results.add(pool.submit(() -> {
                start.await();
                return send(token, name, same);
            }));
        }
        start.countDown();

        List<Boolean> duplicates = List.of(results.get(0).get().duplicate(), results.get(1).get().duplicate());
        pool.shutdown();

        assertThat(duplicates).containsExactlyInAnyOrder(false, true);
        assertThat(count("SELECT count(*) FROM document WHERE project_id = '" + project + "'")).isOne();
    }

    @Test
    void aFileAlsoInAProjectTheWorkerDoesNotSeeIsANewDocumentAndTheSqlAndRlsBarriersHold() {
        String visible = project();
        String hidden = project();
        Actor marta = worker(List.of(visible));
        String text = "mismo contenido en dos proyectos " + COUNTER.incrementAndGet();
        api.upload("/v1/admin/companies/" + company + "/documents?projectId=" + hidden, "ajeno.txt",
                text.getBytes(StandardCharsets.UTF_8), "Cookie", adminCookie);
        String token = tokenOf(issueUploadLink.forProject(marta, new ProjectId(UUID.fromString(visible))));

        UploadReceipt receipt = send(token, "mio.txt", text);

        assertThat(receipt.duplicate()).isFalse();
        assertThat(count("SELECT count(*) FROM document WHERE project_id = '" + visible + "'")).isOne();
        assertThat(count("SELECT count(*) FROM document WHERE project_id = '" + hidden + "'")).isOne();
        // la RLS sola, con el rol de la aplicación y sin ningún filtro propio: del proyecto oculto no se ve nada
        assertThat(rlsCount("document", visible)).isOne();
        assertThat(rlsCount("document", "")).isZero();
        assertThatThrownBy(() -> issueUploadLink.forProject(marta, new ProjectId(UUID.fromString(hidden))))
                .isInstanceOf(NotFoundException.class).hasMessage("Proyecto no encontrado");
    }

    @Test
    void theAdminListNamesTheWorkerWhoUploadedThroughTheLink() {
        String project = project();
        Actor marta = worker(List.of(project));
        String token = tokenOf(issueUploadLink.forProject(marta, new ProjectId(UUID.fromString(project))));
        String name = "autor-" + COUNTER.incrementAndGet() + ".txt";

        send(token, name, "contenido del autor " + COUNTER.incrementAndGet());

        var listed = api.get("/v1/admin/companies/" + company + "/documents?limit=500", "Cookie", adminCookie).json();
        var mine = java.util.stream.StreamSupport.stream(listed.spliterator(), false)
                .filter(d -> d.get("filename").asString().equals(name)).toList();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("uploadedByName").asString()).isEqualTo("Marta Gil");
    }

    @Test
    void leavingTheProjectOrDeletingItStopsTheLink() {
        String project = project();
        Actor marta = worker(List.of(project));
        String token = tokenOf(issueUploadLink.forProject(marta, new ProjectId(UUID.fromString(project))));
        assertThat(describeUploadLink.execute(token, OFFICE).projectName()).startsWith("Obra");

        assertThat(api.delete("/v1/admin/companies/" + company + "/projects/" + project, "Cookie", adminCookie)
                .status()).isIn(200, 204);

        assertThatThrownBy(() -> send(token, "a.txt", "tras borrar " + COUNTER.incrementAndGet()))
                .isInstanceOf(NotFoundException.class).hasMessage("Enlace no encontrado");
    }

    private static int rlsCount(String table, String projectIds) {
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), "odin_app", "app-test-password")) {
            c.setAutoCommit(false);
            try (var st = c.prepareStatement("SELECT set_config('app.company_ids', ?, true), "
                    + "set_config('app.group_scope', 'off', true), set_config('app.all_projects', 'off', true), "
                    + "set_config('app.project_ids', ?, true), set_config('app.include_withdrawn', 'off', true)")) {
                st.setString(1, company);
                st.setString(2, projectIds);
                st.execute();
            }
            try (var st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM " + table)) {
                rs.next();
                int n = rs.getInt(1);
                c.rollback();
                return n;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long count(String sql) {
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
