package com.e2d.odin.bootstrap;

import static com.e2d.odin.bootstrap.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.bootstrap.ApiClient.Reply;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

/**
 * La primera carga de principio a fin por la API, con un subconjunto del archivo revuelto del estudio (semilla 13, en
 * {@code intake-battery/}): subida cortada y repetida sin duplicar, lectura, descubrimiento y confirmación de proyectos,
 * propuestas y confirmación por grupos. En cada paso, la clave de un trabajador de cada proyecto y la de otra empresa
 * (D1): lo pendiente no lo ve nadie y lo confirmado lo ven solo los de su proyecto. La verdad solo sirve para medir.
 */
@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"odin.intake.classify-interval=PT0.3S", "odin.public-url=http://panel.test"})
@Timeout(180)
class IntakeFirstLoadIT {

    private static final Path BATTERY = Path.of("src/test/resources/intake-battery");
    private static final String PASSWORD = "clave-muy-larga-1";
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
        registry.add("ODIN_SETUP_TOKEN", () -> "setup-intake-token");
        registry.add("ODIN_PANEL_ORIGIN", () -> "http://panel.test");
        registry.add("ODIN_ORIGINALS_DIR", () -> System.getProperty("java.io.tmpdir"));
        registry.add("ODIN_UPLOAD_MAX_BYTES", () -> "1000000");
    }

    /** Un fichero del subconjunto y lo que dice la verdad del estudio de él. */
    record Truth(String file, String project, String kind, String copyOf) {
    }

    @LocalServerPort int port;
    private ApiClient api;
    private String cookie;
    private String company;
    private String base;
    private final List<Truth> truth = new ArrayList<>();
    private final Map<String, String> workerKeys = new HashMap<>();
    private final Map<String, String> fileOfDocument = new HashMap<>();

    @Test
    void firstLoadFromUploadToWorkersSeeingOnlyWhatIsConfirmed() throws Exception {
        loadTruth();
        api = new ApiClient(port);
        startInstallation();
        Map<String, String> workerOfProject = Map.of("calle abeto 16", "w1", "calle olmo 47", "w2", "calle olmo 4", "w3");
        for (String worker : List.of("w1", "w2", "w3")) {
            workerKeys.put(worker, workerKey(company, worker));
        }
        workerKeys.put("otra", workerKey(createCompany("Otra empresa", "OT"), "otra"));
        base = "/v1/admin/companies/" + company + "/intake-batches";
        String batch = start();
        uploadCutAndResumed(batch);
        awaitBatch(batch, view -> view.get("reading").asInt() == 0 && view.get("pending").asInt() > 0);

        Map<String, String> projectIds = confirmDiscoveredProjects(batch);
        assertThat(projectIds.keySet()).containsExactlyInAnyOrder("calle abeto 16", "calle olmo 47", "calle olmo 4");
        workerOfProject.forEach((project, worker) -> assign(projectIds.get(project), worker));
        awaitBatch(batch, view -> view.get("state").asString().equals("IN_REVIEW"));
        Set<String> all = documentIds();

        assertNobodySees(all, "pendiente");
        JsonNode groups = proposals(batch);
        assertProjectProposalsMatchTruth(groups, projectIds);
        for (String project : List.of("calle abeto 16", "calle olmo 47", "calle olmo 4")) {
            confirmGroup(batch, groups, projectIds.get(project));
            assertSeenOnlyBy(workerOfProject.get(project), project);
            assertNobodyElseSees(project, workerOfProject);
        }
        placeWhatIsLeft(batch);
        assertThat(api.get(base + "/" + batch, "Cookie", cookie).json().get("unplaced").asInt()).isZero();
    }

    // ---------- Recorrido ----------

    private void uploadCutAndResumed(String batch) throws IOException {
        List<String> files = truth.stream().map(Truth::file).toList();
        int cutAt = files.size() / 2;
        files.subList(0, cutAt).forEach(f -> upload(batch, f));
        // la orden se vuelve a lanzar con la carpeta entera: lo ya subido se reconoce y no se duplica
        Map<Integer, Integer> outcomes = new HashMap<>();
        files.forEach(f -> outcomes.merge(upload(batch, f).status(), 1, Integer::sum));
        long copies = truth.stream().filter(t -> !t.copyOf().isEmpty()).count();
        assertThat(outcomes.get(200)).as("ya estaban").isEqualTo(cutAt + (int) copies);
        assertThat(outcomes.get(201)).as("nuevos en la segunda pasada").isEqualTo(files.size() - cutAt - (int) copies);
        assertThat(api.post(base + "/" + batch + "/close-upload", map(), "Cookie", cookie).status()).isEqualTo(200);
        JsonNode view = api.get(base + "/" + batch, "Cookie", cookie).json();
        assertThat(view.get("uploaded").asInt()).as("documentos distintos, sin duplicados").isEqualTo(files.size() - (int) copies);
        assertThat(fileOfDocument.keySet()).hasSize(files.size() - (int) copies);
    }

    private Reply upload(String batch, String file) {
        try {
            String path = URLEncoder.encode("Cliente/" + file, StandardCharsets.UTF_8);
            Reply reply = api.upload(base + "/" + batch + "/files?path=" + path, file, Files.readAllBytes(BATTERY.resolve(file)),
                    "Cookie", cookie);
            assertThat(reply.status()).isIn(200, 201);
            fileOfDocument.put(reply.json().get("document").get("id").asString(), file);
            return reply;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> confirmDiscoveredProjects(String batch) {
        JsonNode found = api.post(base + "/" + batch + "/project-candidates/discover", map(), "Cookie", cookie).json();
        List<Object> decisions = new ArrayList<>();
        found.get("candidates").forEach(c -> decisions.add(map("name", c.get("name").asString(), "decision", "CREATE")));
        assertThat(api.post(base + "/" + batch + "/project-candidates/confirm", map("decisions", decisions), "Cookie", cookie)
                .status()).isEqualTo(200);
        Map<String, String> ids = new HashMap<>();
        api.get(base + "/" + batch + "/project-candidates", "Cookie", cookie).json().get("candidates")
                .forEach(c -> ids.put(c.get("name").asString().toLowerCase(), c.get("projectId").asString()));
        return ids;
    }

    private void assertProjectProposalsMatchTruth(JsonNode groups, Map<String, String> projectIds) {
        Map<String, String> nameOf = new HashMap<>();
        projectIds.forEach((name, id) -> nameOf.put(id, name));
        int checked = 0;
        for (JsonNode group : groups.get("groups")) {
            if (!group.get("kind").asString().equals("PROJECT") || !group.get("confidence").asString().equals("HIGH")) {
                continue;
            }
            for (JsonNode item : group.get("proposals")) {
                assertThat(nameOf.get(group.get("projectId").asString())).as(item.get("title").asString())
                        .isEqualTo(truthOf(item.get("title").asString()).project().toLowerCase());
                checked++;
            }
        }
        assertThat(checked).as("documentos con proyecto de confianza alta").isEqualTo(9);
    }

    private void confirmGroup(String batch, JsonNode groups, String projectId) {
        List<String> ids = new ArrayList<>();
        for (JsonNode group : groups.get("groups")) {
            if (group.get("kind").asString().equals("PROJECT") && group.get("confidence").asString().equals("HIGH")
                    && group.get("projectId").asString().equals(projectId)) {
                group.get("proposals").forEach(p -> ids.add(p.get("id").asString()));
            }
        }
        Reply confirmed = api.post(base + "/" + batch + "/proposals/confirm", map("proposalIds", ids), "Cookie", cookie);
        assertThat(confirmed.json().get("confirmed").asInt()).isEqualTo(ids.size());
    }

    private void placeWhatIsLeft(String batch) {
        JsonNode left = api.get(base + "/" + batch + "/documents?unplaced=true", "Cookie", cookie).json().get("documents");
        assertThat(left).as("las dos normas sin proyecto").hasSize(2);
        String keep = left.get(0).get("id").asString();
        String drop = left.get(1).get("id").asString();
        assertThat(api.post(base + "/" + batch + "/documents/" + keep + "/place", map("companyWide", true), "Cookie", cookie)
                .status()).isIn(200, 204);
        assertThat(api.post(base + "/" + batch + "/documents/" + drop + "/withdraw", map(), "Cookie", cookie).status())
                .isIn(200, 204);
        assertThat(visibleTo("w1")).contains(keep).doesNotContain(drop);
        assertThat(visibleTo("otra")).isEmpty();
    }

    // ---------- D1: lo que ve un trabajador ----------

    private void assertNobodySees(Set<String> documents, String why) {
        for (String worker : workerKeys.keySet()) {
            assertThat(visibleTo(worker)).as(worker + " con todo " + why).isEmpty();
            for (String document : documents) {
                assertThat(readable(worker, document)).as(worker + " lee " + document).isFalse();
            }
        }
    }

    private void assertSeenOnlyBy(String worker, String project) {
        Set<String> expected = idsOf(project);
        assertThat(visibleTo(worker)).as(worker + " ve los de " + project).containsAll(expected);
        for (String id : expected) {
            assertThat(readable(worker, id)).isTrue();
            assertThat(searchFinds(worker, id)).as(worker + " encuentra " + id).isTrue();
            assertThat(originalLinkStatus(worker, id)).isEqualTo(201);
        }
    }

    private void assertNobodyElseSees(String project, Map<String, String> workerOfProject) {
        for (String worker : workerKeys.keySet()) {
            if (worker.equals(workerOfProject.get(project))) {
                continue;
            }
            for (String id : idsOf(project)) {
                assertThat(readable(worker, id)).as(worker + " lee " + id + " de " + project).isFalse();
                assertThat(searchFinds(worker, id)).as(worker + " encuentra " + id).isFalse();
                assertThat(visibleTo(worker)).doesNotContain(id);
                assertThat(originalLinkStatus(worker, id)).isEqualTo(404);
            }
        }
    }

    private Set<String> visibleTo(String worker) {
        Set<String> ids = new HashSet<>();
        api.get("/v1/documents?limit=200", "Authorization", workerKeys.get(worker)).json()
                .forEach(d -> ids.add(d.get("id").asString()));
        return ids;
    }

    private boolean readable(String worker, String document) {
        return api.get("/v1/documents/" + document + "/pages/1", "Authorization", workerKeys.get(worker)).status() == 200;
    }

    private int originalLinkStatus(String worker, String document) {
        return api.post("/v1/documents/" + document + "/original-links", map(), "Authorization", workerKeys.get(worker))
                .status();
    }

    private boolean searchFinds(String worker, String document) {
        Reply found = api.post("/v1/search", map("queries", List.of(queryOf(fileOfDocument.get(document))), "limit", 20),
                "Authorization", workerKeys.get(worker));
        for (JsonNode hit : found.json().get("hits")) {
            if (hit.get("sourceId").asString().equals(document)) {
                return true;
            }
        }
        return false;
    }

    // ---------- Apoyo ----------

    private Set<String> idsOf(String project) {
        Set<String> ids = new HashSet<>();
        fileOfDocument.forEach((id, file) -> {
            if (truthOf(file).project().equalsIgnoreCase(project)) {
                ids.add(id);
            }
        });
        return ids;
    }

    private Set<String> documentIds() {
        return new HashSet<>(fileOfDocument.keySet());
    }

    private JsonNode proposals(String batch) {
        return api.get(base + "/" + batch + "/proposals", "Cookie", cookie).json();
    }

    private Truth truthOf(String file) {
        return truth.stream().filter(t -> t.file().equals(file)).findFirst().orElseThrow();
    }

    /** La línea más larga del documento (la más específica), recortada a 30 palabras. */
    private static String queryOf(String file) {
        try {
            String longest = Files.readAllLines(BATTERY.resolve(file)).stream().filter(l -> !l.startsWith("=== "))
                    .max(java.util.Comparator.comparingInt(String::length)).orElseThrow();
            return String.join(" ", java.util.Arrays.stream(longest.split("\\s+")).limit(30).toList());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void loadTruth() throws IOException {
        for (String line : Files.readAllLines(BATTERY.resolve("truth.tsv"))) {
            String[] f = line.split("\t", -1);
            truth.add(new Truth(f[0], f[1], f[2], f[3]));
        }
    }

    private void awaitBatch(String batch, Predicate<JsonNode> done) throws InterruptedException {
        JsonNode view = null;
        for (int i = 0; i < 240; i++) {
            view = api.get(base + "/" + batch, "Cookie", cookie).json();
            if (done.test(view)) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("El lote no llegó al estado esperado: " + view);
    }

    private String start() {
        Reply started = api.post(base, map("channel", "COMMAND"), "Cookie", cookie);
        assertThat(started.status()).isEqualTo(201);
        return started.json().get("id").asString();
    }

    private void assign(String project, String worker) {
        String user = workerUsers.get(worker);
        assertThat(api.put("/v1/admin/companies/" + company + "/projects/" + project + "/members/" + user, map(), "Cookie",
                cookie).status()).isIn(200, 204);
    }

    private final Map<String, String> workerUsers = new HashMap<>();
    private String groupCookie;

    private void startInstallation() {
        assertThat(api.post("/v1/setup", map("setupToken", "setup-intake-token", "groupName", "Grupo", "adminName", "Admin",
                "adminEmail", "grupo@ejemplo.test", "password", PASSWORD)).status()).isEqualTo(201);
        groupCookie = api.post("/v1/admin/session", map("email", "grupo@ejemplo.test", "password", PASSWORD)).setCookie()
                .split(";")[0];
        company = createCompany("Administraciones Ribera", "RB");
        assertThat(api.post("/v1/admin/companies/" + company + "/members", map("firstName", "Ana", "lastName", "Admin",
                "email", "ana@ejemplo.test", "roles", List.of("COMPANY_ADMIN"), "password", PASSWORD), "Cookie", groupCookie)
                .status()).isEqualTo(201);
        cookie = api.post("/v1/admin/session", map("email", "ana@ejemplo.test", "password", PASSWORD)).setCookie().split(";")[0];
    }

    private String createCompany(String name, String code) {
        Reply created = api.post("/v1/admin/companies", map("name", name, "code", code), "Cookie", groupCookie);
        assertThat(created.status()).isEqualTo(201);
        String id = created.json().get("id").asString();
        assertThat(api.put("/v1/admin/companies/" + id + "/capabilities",
                map("capabilities", List.of("SEARCH_KNOWLEDGE", "SAVE_KNOWLEDGE_CARD", "SIMILAR_CASES")), "Cookie",
                groupCookie).status()).isEqualTo(200);
        return id;
    }

    private String workerKey(String companyId, String name) {
        Reply member = api.post("/v1/admin/companies/" + companyId + "/members",
                map("firstName", name, "lastName", "Prueba", "roles", List.of("WORKER")), "Cookie", groupCookie);
        assertThat(member.status()).isEqualTo(201);
        String user = member.json().get("member").get("userId").asString();
        workerUsers.put(name, user);
        Reply key = api.post("/v1/admin/companies/" + companyId + "/members/" + user + "/keys", map("label", "prueba"),
                "Cookie", groupCookie);
        assertThat(key.status()).isEqualTo(201);
        return "Bearer " + key.json().get("key").asString();
    }
}
