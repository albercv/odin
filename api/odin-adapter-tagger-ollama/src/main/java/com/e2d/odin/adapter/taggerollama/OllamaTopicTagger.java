package com.e2d.odin.adapter.taggerollama;

import com.e2d.odin.application.port.TaggerAnswer;
import com.e2d.odin.application.port.TaggerFailedException;
import com.e2d.odin.application.port.TaggerUnavailableException;
import com.e2d.odin.application.port.TopicTagger;
import com.e2d.odin.application.topic.TaggingInstructions;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Etiquetador sobre la API HTTP de Ollama, con las condiciones medidas en T-010. */
public class OllamaTopicTagger implements TopicTagger {

    static final int CONTEXT_TOKENS = 4096;
    private static final int MAX_ANSWER_TOKENS = 60;
    private static final Duration AVAILABILITY_TIMEOUT = Duration.ofSeconds(5);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final URI baseUrl;
    private final String model;
    private final Duration timeout;
    private final OptionalInt numGpu;
    private final OptionalInt numThread;
    private final HttpClient client;

    public OllamaTopicTagger(URI baseUrl, String model, Duration timeout, OptionalInt numGpu, OptionalInt numThread,
            HttpClient client) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = timeout;
        this.numGpu = numGpu;
        this.numThread = numThread;
        this.client = client;
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpResponse<String> response = client.send(request("/api/tags", AVAILABILITY_TIMEOUT).GET().build(),
                    BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() == 200 && hasModel(MAPPER.readTree(response.body()).path("models"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | JacksonException e) {
            return false;
        }
    }

    private boolean hasModel(JsonNode models) {
        for (JsonNode installed : models) {
            String name = installed.path("name").asString("");
            if (name.equals(model) || name.equals(model + ":latest")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TaggerAnswer tag(String instructions) {
        HttpRequest request = request("/api/generate", timeout).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(instructions), StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
            throw new TaggerFailedException("Ollama respondió " + response.statusCode());
        }
        return parse(response.body());
    }

    private String body(String instructions) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", 0);
        options.put("num_predict", MAX_ANSWER_TOKENS);
        options.put("num_ctx", CONTEXT_TOKENS);
        numGpu.ifPresent(value -> options.put("num_gpu", value));
        numThread.ifPresent(value -> options.put("num_thread", value));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("prompt", instructions);
        body.put("stream", false);
        body.put("think", false);
        body.put("options", options);
        return MAPPER.writeValueAsString(body);
    }

    private TaggerAnswer parse(String body) {
        try {
            JsonNode root = MAPPER.readTree(body);
            if (!root.path("response").isString() || !root.path("prompt_eval_count").isNumber()) {
                throw new TaggerFailedException("La respuesta de Ollama no trae el texto o los tokens del prompt");
            }
            return new TaggerAnswer(root.path("response").asString(), root.path("prompt_eval_count").asInt(),
                    CONTEXT_TOKENS, model + "; " + TaggingInstructions.VERSION);
        } catch (JacksonException e) {
            throw new TaggerFailedException("La respuesta de Ollama no es JSON", e);
        }
    }

    private HttpRequest.Builder request(String path, Duration requestTimeout) {
        return HttpRequest.newBuilder(URI.create(baseUrl.toString().replaceAll("/+$", "") + path))
                .timeout(requestTimeout);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpConnectTimeoutException | ConnectException e) {
            throw new TaggerUnavailableException("No se puede conectar con Ollama", e);
        } catch (HttpTimeoutException e) {
            throw new TaggerFailedException("Ollama no respondió a tiempo", e);
        } catch (IOException e) {
            throw new TaggerFailedException("Fallo de comunicación con Ollama", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaggerFailedException("Etiquetado interrumpido", e);
        }
    }
}
