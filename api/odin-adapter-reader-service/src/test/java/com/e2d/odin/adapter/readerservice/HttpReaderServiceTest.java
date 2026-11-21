package com.e2d.odin.adapter.readerservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.port.DocumentTooLargeForReaderException;
import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.application.port.ReaderFailedException;
import com.e2d.odin.application.port.ReaderTimedOutException;
import com.e2d.odin.application.port.ReaderUnavailableException;
import com.e2d.odin.application.port.UnreadableDocumentException;
import com.e2d.odin.domain.document.DocumentFormat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpReaderServiceTest {

    private static final Path EXAMPLES = Path.of("../../docs/contratos/ejemplos/servicio-lector");
    private static final byte[] CONTENT = "contenido ficticio".getBytes(StandardCharsets.UTF_8);

    private final List<Seen> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private int status = 200;
    private String body = "";
    private long delayMillis;

    private record Seen(String method, String path, com.sun.net.httpserver.Headers headers, String body) { }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
        requests.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders(), received));
        sleep();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private void sleep() {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpReaderService service(Optional<String> token) {
        return service(token, Duration.ofSeconds(5), Duration.ofSeconds(5));
    }

    private HttpReaderService service(Optional<String> token, Duration readTimeout, Duration connectTimeout) {
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new HttpReaderService(base, readTimeout, connectTimeout, token,
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
    }

    private static String example(String name) throws IOException {
        return Files.readString(EXAMPLES.resolve(name));
    }

    private void respond(int newStatus, String newBody) {
        status = newStatus;
        body = newBody;
    }

    private ReadResult readOk() {
        return service(Optional.empty()).read(CONTENT, DocumentFormat.IMAGE);
    }

    @Test
    void sendsMultipartWithFileField() throws IOException {
        respond(200, example("read-200.json"));
        readOk();
        Seen seen = requests.get(0);
        assertThat(seen.method()).isEqualTo("POST");
        assertThat(seen.path()).isEqualTo("/read");
        assertThat(seen.headers().getFirst("Content-Type")).startsWith("multipart/form-data; boundary=");
        assertThat(seen.body()).contains("name=\"file\"; filename=\"document\"").contains("contenido ficticio");
    }

    @Test
    void sendsContentLengthNotChunked() throws IOException {
        respond(200, example("read-200.json"));
        readOk();
        Seen seen = requests.get(0);
        assertThat(seen.headers().getFirst("Content-Length")).isEqualTo(String.valueOf(seen.body().length()));
        assertThat(seen.headers().getFirst("Transfer-Encoding")).isNull();
    }

    @Test
    void sendsBearerTokenOnlyWhenConfigured() throws IOException {
        respond(200, example("read-200.json"));
        service(Optional.of("secreto-ficticio")).read(CONTENT, DocumentFormat.IMAGE);
        service(Optional.empty()).read(CONTENT, DocumentFormat.IMAGE);
        assertThat(requests.get(0).headers().getFirst("Authorization")).isEqualTo("Bearer secreto-ficticio");
        assertThat(requests.get(1).headers().getFirst("Authorization")).isNull();
    }

    @Test
    void healthOkIsAvailable() throws IOException {
        respond(200, example("health-200.json"));
        assertThat(service(Optional.empty()).isAvailable()).isTrue();
        assertThat(requests.get(0).path()).isEqualTo("/health");
    }

    @Test
    void healthWithAnotherStatusIsNotAvailable() {
        respond(200, "{\"status\":\"loading\",\"reader\":{\"name\":\"a\",\"version\":\"b\"}}");
        assertThat(service(Optional.empty()).isAvailable()).isFalse();
        respond(500, "");
        assertThat(service(Optional.empty()).isAvailable()).isFalse();
    }

    @Test
    void healthTimesOutWhenTheServiceIsBusy() throws IOException {
        respond(200, example("health-200.json"));
        delayMillis = 6000; // margen: timeout 300 ms << umbral 2500 ms << retraso 6000 ms; solo falla si el timeout no corta
        long start = System.nanoTime();
        assertThat(service(Optional.empty(), Duration.ofSeconds(5), Duration.ofMillis(300)).isAvailable()).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2500));
    }

    @Test
    void connectionRefusedIsUnavailable() {
        HttpReaderService refused = service(Optional.empty());
        server.stop(0);
        assertThat(refused.isAvailable()).isFalse();
        assertThatThrownBy(() -> refused.read(CONTENT, DocumentFormat.IMAGE))
                .isInstanceOf(ReaderUnavailableException.class);
    }

    @Test
    void unauthorizedIsUnavailable() throws IOException {
        respond(401, example("read-401.json"));
        assertThatThrownBy(this::readOk).isInstanceOf(ReaderUnavailableException.class);
    }

    @Test
    void tooLargeIsDocumentTooLarge() throws IOException {
        respond(413, example("read-413.json"));
        assertThatThrownBy(this::readOk).isInstanceOf(DocumentTooLargeForReaderException.class);
    }

    @Test
    void unsupportedOrUnreadableIsUnreadable() throws IOException {
        respond(415, example("read-415.json"));
        assertThatThrownBy(this::readOk).isInstanceOf(UnreadableDocumentException.class);
        respond(422, example("read-422.json"));
        assertThatThrownBy(this::readOk).isInstanceOf(UnreadableDocumentException.class);
    }

    @Test
    void badRequestOrNotFoundIsReaderFailure() throws IOException {
        respond(400, example("read-400.json"));
        assertThatThrownBy(this::readOk).isInstanceOf(ReaderFailedException.class);
        respond(404, "");
        assertThatThrownBy(this::readOk).isInstanceOf(ReaderFailedException.class);
    }

    @Test
    void serverErrorIsReaderFailure() throws IOException {
        respond(500, example("read-500.json"));
        assertThatThrownBy(this::readOk).isInstanceOf(ReaderFailedException.class);
    }

    @Test
    void readTimeoutIsTimedOutFailure() throws IOException {
        respond(200, example("read-200.json"));
        delayMillis = 6000; // margen: timeout 300 ms << umbral 2500 ms << retraso 6000 ms; solo falla si el timeout no corta
        HttpReaderService slow = service(Optional.empty(), Duration.ofMillis(200), Duration.ofSeconds(5));
        assertThatThrownBy(() -> slow.read(CONTENT, DocumentFormat.IMAGE))
                .isInstanceOf(ReaderTimedOutException.class);
    }

    @Test
    void pagesWithGapIsReaderFailure() {
        respond(200, pages("{\"number\":1,\"text\":\"a\"},{\"number\":3,\"text\":\"c\"}", reader("n", "v")));
        assertThatThrownBy(this::readOk).isInstanceOf(ReaderFailedException.class);
        respond(200, pages("{\"number\":1,\"text\":\"a\"},{\"number\":1,\"text\":\"b\"}", reader("n", "v")));
        assertThatThrownBy(this::readOk).isInstanceOf(ReaderFailedException.class);
    }

    @Test
    void missingReaderIsReaderFailure() {
        respond(200, "{\"pages\":[{\"number\":1,\"text\":\"a\"}]}");
        assertThatThrownBy(this::readOk).isInstanceOf(ReaderFailedException.class);
        respond(200, pages("{\"number\":1,\"text\":\"a\"}", reader("", "v")));
        assertThatThrownBy(this::readOk).isInstanceOf(ReaderFailedException.class);
        respond(200, "no es json");
        assertThatThrownBy(this::readOk).isInstanceOf(ReaderFailedException.class);
    }

    @Test
    void returnsPagesInOrderAsPaginated() {
        respond(200, pages("{\"number\":2,\"text\":\"\"},{\"number\":1,\"text\":\"uno\"}", reader("ocr", "1.0")));
        ReadResult result = readOk();
        assertThat(result.pageTexts()).containsExactly("uno", "");
        assertThat(result.paginated()).isTrue();
        assertThat(result.readerName()).isEqualTo("ocr");
        assertThat(result.readerVersion()).isEqualTo("1.0");
    }

    private static String reader(String name, String version) {
        return "{\"name\":\"" + name + "\",\"version\":\"" + version + "\"}";
    }

    private static String pages(String pages, String reader) {
        return "{\"pages\":[" + pages + "],\"reader\":" + reader + "}";
    }
}
