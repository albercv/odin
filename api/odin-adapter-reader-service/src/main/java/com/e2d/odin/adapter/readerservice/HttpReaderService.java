package com.e2d.odin.adapter.readerservice;

import com.e2d.odin.application.port.DocumentTooLargeForReaderException;
import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.application.port.ReaderFailedException;
import com.e2d.odin.application.port.ReaderService;
import com.e2d.odin.application.port.ReaderTimedOutException;
import com.e2d.odin.application.port.ReaderUnavailableException;
import com.e2d.odin.application.port.UnreadableDocumentException;
import com.e2d.odin.domain.document.DocumentFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cliente HTTP del servicio lector (contrato en docs/contratos/servicio-lector.openapi.yaml). */
public class HttpReaderService implements ReaderService {

    private static final System.Logger LOG = System.getLogger(HttpReaderService.class.getName());
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final URI baseUrl;
    private final Duration readTimeout;
    private final Duration connectTimeout;
    private final Optional<String> token;
    private final HttpClient client;

    public HttpReaderService(URI baseUrl, Duration readTimeout, Duration connectTimeout, Optional<String> token,
            HttpClient client) {
        this.baseUrl = baseUrl;
        this.readTimeout = readTimeout;
        this.connectTimeout = connectTimeout;
        this.token = token;
        this.client = client;
    }

    @Override
    public boolean isAvailable() {
        try {
            // mientras el puente lee, acepta la conexión pero no responde: el tiempo límite va en la petición
            HttpResponse<String> response = client.send(request("/health", connectTimeout).GET().build(),
                    BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() == 200 && "ok".equals(MAPPER.readTree(response.body()).path("status")
                    .asString(""));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | JacksonException e) {
            return false;
        }
    }

    @Override
    public ReadResult read(byte[] content, DocumentFormat format) {
        String boundary = "odin-" + UUID.randomUUID();
        HttpRequest request = request("/read", readTimeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(BodyPublishers.ofByteArray(multipart(boundary, content))).build();
        HttpResponse<String> response = send(request);
        return switch (response.statusCode()) {
            case 200 -> parse(response.body());
            case 413 -> throw new DocumentTooLargeForReaderException("El servicio lector rechaza el tamaño (413)");
            case 415, 422 -> throw new UnreadableDocumentException("El servicio lector no puede leer el fichero ("
                    + response.statusCode() + ")");
            case 400, 404 -> clientFailure(response.statusCode());
            case 401 -> unauthorized();
            default -> throw new ReaderFailedException("El servicio lector respondió " + response.statusCode());
        };
    }

    private ReadResult clientFailure(int status) {
        LOG.log(System.Logger.Level.WARNING, "El servicio lector respondió " + status
                + ": la petición de Odín no cumple el contrato o la URL no es la del servicio");
        throw new ReaderFailedException("El servicio lector respondió " + status);
    }

    private ReadResult unauthorized() {
        LOG.log(System.Logger.Level.ERROR, "El servicio lector rechaza el secreto (401): revisa ODIN_READER_TOKEN");
        throw new ReaderUnavailableException("El servicio lector rechaza el secreto (401)", null);
    }

    private HttpRequest.Builder request(String path, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl.toString().replaceAll("/+$", "") + path))
                .timeout(timeout);
        token.filter(value -> !value.isBlank()).ifPresent(value -> builder.header("Authorization", "Bearer " + value));
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpConnectTimeoutException | ConnectException e) {
            throw new ReaderUnavailableException("No se puede conectar con el servicio lector", e);
        } catch (HttpTimeoutException e) {
            throw new ReaderTimedOutException("El servicio lector no respondió a tiempo", e);
        } catch (IOException e) {
            throw new ReaderFailedException("Fallo de comunicación con el servicio lector", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReaderFailedException("Lectura interrumpida", e);
        }
    }

    private static byte[] multipart(String boundary, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(content.length + 256);
        out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document\""
                + "\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.writeBytes(content);
        out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static ReadResult parse(String body) {
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode reader = root.path("reader");
            String name = reader.path("name").asString("");
            String version = reader.path("version").asString("");
            if (name.isBlank() || version.isBlank()) {
                throw new ReaderFailedException("La respuesta del servicio lector no indica el lector");
            }
            return new ReadResult(pageTexts(root.path("pages")), true, name, version);
        } catch (JacksonException e) {
            throw new ReaderFailedException("La respuesta del servicio lector no es JSON", e);
        }
    }

    private static List<String> pageTexts(JsonNode pages) {
        if (!pages.isArray() || pages.isEmpty()) {
            throw new ReaderFailedException("La respuesta del servicio lector no trae páginas");
        }
        List<JsonNode> sorted = new ArrayList<>();
        pages.forEach(sorted::add);
        sorted.sort(Comparator.comparingInt(page -> page.path("number").asInt(-1)));
        List<String> texts = new ArrayList<>();
        for (JsonNode page : sorted) {
            if (page.path("number").asInt(-1) != texts.size() + 1 || !page.path("text").isString()) {
                throw new ReaderFailedException("Las páginas del servicio lector no van de 1 a n sin huecos");
            }
            texts.add(page.path("text").asString());
        }
        return texts;
    }
}
