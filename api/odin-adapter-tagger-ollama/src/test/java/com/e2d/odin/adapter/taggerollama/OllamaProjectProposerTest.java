package com.e2d.odin.adapter.taggerollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.port.TaggerAnswer;
import com.e2d.odin.application.port.TaggerFailedException;
import com.e2d.odin.application.port.TaggerUnavailableException;
import com.e2d.odin.application.port.TopicTagger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OllamaProjectProposerTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final List<String> PROJECTS = List.of("Calle Olmo 24", "Calle Olmo 24 (bloque 2)", "Las Acacias");

    private final List<String> generateBodies = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private String generateResponse;
    private long generateDelayMillis;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
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
        byte[] bytes = generateResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String answer(String text) {
        return "{\"response\":" + MAPPER.writeValueAsString(text) + ",\"prompt_eval_count\":100}";
    }

    private OllamaProjectProposer proposer(Duration timeout) {
        return new OllamaProjectProposer(new OllamaTopicTagger(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "qwen3.5:9b", timeout,
                OptionalInt.empty(), OptionalInt.empty(), HttpClient.newHttpClient()));
    }

    private Optional<String> propose(String modelAnswer) {
        generateResponse = answer(modelAnswer);
        return proposer(Duration.ofSeconds(5)).propose(PROJECTS, "Informe de la comunidad");
    }

    @Test
    void anExactNameFromTheListIsProposed() {
        assertThat(propose("Las Acacias")).contains("Las Acacias");
    }

    @Test
    void theNameIsMatchedIgnoringCaseAccentsAndSpacesAndReturnedAsInTheList() {
        assertThat(propose("  calle OLMO 24 (Bloque 2). ")).contains("Calle Olmo 24 (bloque 2)");
    }

    @Test
    void anAnswerNotInTheListIsDiscarded() {
        assertThat(propose("Calle Olmo 25")).isEmpty();
        assertThat(propose("Creo que es Las Acacias, pero no estoy seguro")).isEmpty();
    }

    @Test
    void noneProposesNothing() {
        assertThat(propose("ninguno")).isEmpty();
        assertThat(propose("Ninguno.")).isEmpty();
    }

    @Test
    void timeoutProposesNothingAndDoesNotBreakTheBatch() {
        generateResponse = answer("Las Acacias");
        generateDelayMillis = 1500;
        OllamaProjectProposer proposer = proposer(Duration.ofMillis(300));

        assertThat(proposer.propose(PROJECTS, "texto")).isEmpty();
        generateDelayMillis = 0;
        assertThat(proposer.propose(PROJECTS, "texto")).contains("Las Acacias");
    }

    @Test
    void failuresProposeNothing() {
        TopicTagger unavailable = new FailingTagger(new TaggerUnavailableException("sin Ollama", null));
        TopicTagger failing = new FailingTagger(new TaggerFailedException("500"));

        assertThat(new OllamaProjectProposer(unavailable).propose(PROJECTS, "texto")).isEmpty();
        assertThat(new OllamaProjectProposer(failing).propose(PROJECTS, "texto")).isEmpty();
    }

    @Test
    void asksWithTheListAndOnlyTheFirst2000Characters() throws Exception {
        generateResponse = answer("ninguno");
        String text = "a".repeat(2000) + "ZZZ_FUERA";

        proposer(Duration.ofSeconds(5)).propose(PROJECTS, text);

        JsonNode body = MAPPER.readTree(generateBodies.get(0));
        String prompt = body.get("prompt").asString();
        assertThat(prompt).contains("- Calle Olmo 24 (bloque 2)").contains("- Las Acacias").contains("a".repeat(2000))
                .doesNotContain("ZZZ_FUERA");
        assertThat(body.get("think").asBoolean(true)).isFalse();
        assertThat(body.get("options").get("temperature").asInt(-1)).isZero();
        assertThat(body.get("options").get("num_ctx").asInt()).isEqualTo(4096);
    }

    @Test
    void anEmptyListDoesNotCallTheModel() {
        generateResponse = answer("Las Acacias");

        assertThat(proposer(Duration.ofSeconds(5)).propose(new ArrayList<>(), "texto")).isEmpty();
        assertThat(generateBodies).isEmpty();
    }

    private record FailingTagger(RuntimeException error) implements TopicTagger {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public TaggerAnswer tag(String instructions) {
            throw error;
        }
    }
}
