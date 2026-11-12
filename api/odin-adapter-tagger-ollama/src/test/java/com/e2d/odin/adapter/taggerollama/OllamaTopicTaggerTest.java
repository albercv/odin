package com.e2d.odin.adapter.taggerollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.port.TaggerAnswer;
import com.e2d.odin.application.port.TaggerFailedException;
import com.e2d.odin.application.port.TaggerUnavailableException;
import com.e2d.odin.application.topic.TopicMatcher;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OllamaTopicTaggerTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String MODEL = "qwen3.5:9b";

    private final List<String> generateBodies = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private int generateStatus = 200;
    private String generateResponse = "{\"response\":\"agua\",\"prompt_eval_count\":100}";
    private String tagsResponse = "{\"models\":[{\"name\":\"" + MODEL + "\"}]}";
    private long generateDelayMillis;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/api/tags", exchange -> reply(exchange, 200, tagsResponse));
        server.createContext("/api/generate", this::generate);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void generate(HttpExchange exchange) throws IOException {
        generateBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        try {
            Thread.sleep(generateDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        reply(exchange, generateStatus, generateResponse);
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private OllamaTopicTagger tagger(OptionalInt numGpu, OptionalInt numThread, Duration timeout) {
        return new OllamaTopicTagger(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), MODEL, timeout,
                numGpu, numThread, HttpClient.newHttpClient());
    }

    private OllamaTopicTagger tagger() {
        return tagger(OptionalInt.empty(), OptionalInt.empty(), Duration.ofSeconds(5));
    }

    @Test
    void sendsMeasuredOptions() throws Exception {
        tagger().tag("Instrucciones montadas");

        JsonNode body = MAPPER.readTree(generateBodies.get(0));
        assertThat(body.get("model").asString()).isEqualTo(MODEL);
        assertThat(body.get("prompt").asString()).isEqualTo("Instrucciones montadas");
        assertThat(body.get("stream").asBoolean(true)).isFalse();
        assertThat(body.get("think").asBoolean(true)).isFalse();
        JsonNode options = body.get("options");
        assertThat(options.get("temperature").asInt(-1)).isZero();
        assertThat(options.get("num_predict").asInt()).isEqualTo(60);
        assertThat(options.get("num_ctx").asInt()).isEqualTo(4096);
        assertThat(options.has("num_gpu")).isFalse();
        assertThat(options.has("num_thread")).isFalse();
    }

    @Test
    void sendsNumGpuOnlyWhenConfigured() throws Exception {
        tagger(OptionalInt.of(0), OptionalInt.of(6), Duration.ofSeconds(5)).tag("x");

        JsonNode options = MAPPER.readTree(generateBodies.get(0)).get("options");
        assertThat(options.get("num_gpu").asInt(-1)).isZero();
        assertThat(options.get("num_thread").asInt()).isEqualTo(6);
    }

    @Test
    void answerCarriesPromptTokensAndContext() {
        generateResponse = "{\"response\":\"agua y humedades\",\"prompt_eval_count\":4096}";

        TaggerAnswer answer = tagger().tag("x");

        assertThat(answer).isEqualTo(new TaggerAnswer("agua y humedades", 4096, 4096, MODEL + "; instrucciones v1"));
        assertThat(answer.truncated()).isTrue();
    }

    @Test
    void unknownNamesAreIgnored() {
        CompanyId company = new CompanyId(UUID.randomUUID());
        List<Topic> topics = List.of(new Topic(new TopicId(UUID.randomUUID()), company, "agua", "fugas"));
        generateResponse = "{\"response\":\"electricidad; jardín\",\"prompt_eval_count\":50}";

        TaggerAnswer answer = tagger().tag("x");

        assertThat(TopicMatcher.match(answer.text(), topics)).isEmpty();
    }

    @Test
    void modelPresentIsAvailable() {
        assertThat(tagger().isAvailable()).isTrue();
    }

    @Test
    void modelMissingIsUnavailable() {
        tagsResponse = "{\"models\":[{\"name\":\"otro:1b\"}]}";

        assertThat(tagger().isAvailable()).isFalse();
    }

    @Test
    void connectionRefusedIsUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        OllamaTopicTagger tagger = new OllamaTopicTagger(URI.create("http://127.0.0.1:" + closedPort), MODEL,
                Duration.ofSeconds(5), OptionalInt.empty(), OptionalInt.empty(), HttpClient.newHttpClient());

        assertThat(tagger.isAvailable()).isFalse();
        assertThatThrownBy(() -> tagger.tag("x")).isInstanceOf(TaggerUnavailableException.class);
    }

    @Test
    void serverErrorIsFailure() {
        generateStatus = 500;
        assertThatThrownBy(() -> tagger().tag("x")).isInstanceOf(TaggerFailedException.class);
        generateStatus = 404;
        assertThatThrownBy(() -> tagger().tag("x")).isInstanceOf(TaggerFailedException.class);
    }

    @Test
    void timeoutIsFailure() {
        generateDelayMillis = 1500;

        assertThatThrownBy(() -> tagger(OptionalInt.empty(), OptionalInt.empty(), Duration.ofMillis(300)).tag("x"))
                .isInstanceOf(TaggerFailedException.class);
    }
}
