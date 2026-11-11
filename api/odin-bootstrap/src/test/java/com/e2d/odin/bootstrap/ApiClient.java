package com.e2d.odin.bootstrap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class ApiClient {

    record Reply(int status, String contentType, String body, String setCookie) {
        JsonNode json() {
            return MAPPER.readTree(body);
        }
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String BOUNDARY = "odin-test-boundary";

    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;

    ApiClient(int port) {
        this.base = "http://localhost:" + port;
    }

    Reply get(String path, String... headers) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET(), headers);
    }

    Reply post(String path, Object body, String... headers) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(MAPPER.writeValueAsString(body))), headers);
    }

    Reply put(String path, Object body, String... headers) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
                .PUT(BodyPublishers.ofString(MAPPER.writeValueAsString(body))), headers);
    }

    Reply delete(String path, String... headers) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).DELETE(), headers);
    }

    Reply upload(String path, String filename, byte[] content, String... headers) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + filename + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.writeBytes(content);
        out.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(BodyPublishers.ofByteArray(out.toByteArray())), headers);
    }

    static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private Reply send(HttpRequest.Builder request, String... headers) {
        try {
            HttpResponse<String> response = http.send(request.headers(headers.length == 0 ? new String[] {"X-T", "1"}
                    : headers).build(), BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.headers().firstValue("Content-Type").orElse(""),
                    response.body(), response.headers().firstValue("Set-Cookie").orElse(""));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
