package com.e2d.odin.bootstrap;

import static com.e2d.odin.bootstrap.ApiClient.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.application.port.ReaderService;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.bootstrap.ApiClient.Reply;
import com.e2d.odin.domain.document.DocumentFormat;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = OdinApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(ReaderLaneIT.BlockingReaderConfiguration.class)
@TestPropertySource(properties = {"odin.reader.url=http://127.0.0.1:1", "odin.reader.poll-interval=PT0.2S"})
class ReaderLaneIT {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final CountDownLatch ENTERED = new CountDownLatch(1);
    private static final CountDownLatch RELEASE = new CountDownLatch(1);

    @TestConfiguration
    static class BlockingReaderConfiguration {
        @Bean
        @Primary
        ReaderService blockingReader() {
            return new ReaderService() {
                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public ReadResult read(byte[] content, DocumentFormat format) {
                    ENTERED.countDown();
                    await(RELEASE);
                    return new ReadResult(List.of("Texto ficticio leído"), true, "falso", "1");
                }
            };
        }
    }

    private static final String SETUP_TOKEN = "setup-reader-token";
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

    @LocalServerPort int port;
    @Autowired InstallationSettings installation;

    @Test
    void scannedDocumentsSupportedFollowsUrl() {
        assertThat(installation.scannedDocumentsSupported()).isTrue();
    }

    @Test
    void localLaneKeepsRunningWhileReaderLaneIsBlocked() throws InterruptedException {
        ApiClient api = new ApiClient(port);
        String cookie = logIn(api);
        String company = api.post("/v1/admin/companies", map("name", "Empresa L", "code", "EL"), "Cookie", cookie)
                .json().get("id").asString();
        String scanned = upload(api, company, "escaneo.png", PNG, cookie);
        assertThat(ENTERED.await(30, TimeUnit.SECONDS)).as("el lector empezó a leer").isTrue();

        String text = upload(api, company, "nota.txt", "Nota ficticia".getBytes(StandardCharsets.UTF_8), cookie);

        assertThat(awaitStatus(api, text, "READY", cookie)).isEqualTo("READY");
        assertThat(status(api, scanned, cookie)).isEqualTo("READER_PROCESSING");
        RELEASE.countDown();
        assertThat(awaitStatus(api, scanned, "READY", cookie)).isEqualTo("READY");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String logIn(ApiClient api) {
        assertThat(api.post("/v1/setup", map("setupToken", SETUP_TOKEN, "groupName", "Grupo Lector",
                "adminName", "Admin", "adminEmail", "lector@ejemplo.test", "password", "clave-muy-larga-1"))
                .status()).isEqualTo(201);
        Reply login = api.post("/v1/admin/session", map("email", "lector@ejemplo.test",
                "password", "clave-muy-larga-1"));
        return login.setCookie().split(";")[0];
    }

    private String upload(ApiClient api, String company, String name, byte[] content, String cookie) {
        Reply reply = api.upload("/v1/admin/companies/" + company + "/documents", name, content, "Cookie", cookie);
        assertThat(reply.status()).isEqualTo(201);
        return reply.json().get("document").get("id").asString();
    }

    private String status(ApiClient api, String document, String cookie) {
        return api.get("/v1/admin/documents/" + document, "Cookie", cookie).json().get("status").asString();
    }

    private String awaitStatus(ApiClient api, String document, String wanted, String cookie)
            throws InterruptedException {
        String status = "";
        for (int i = 0; i < 80 && !wanted.equals(status); i++) {
            Thread.sleep(500);
            status = status(api, document, cookie);
        }
        return status;
    }
}
