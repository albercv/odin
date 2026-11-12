package com.e2d.odin.adapter.taggerollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.port.TaggerAnswer;
import com.e2d.odin.application.port.TopicTagger;
import com.e2d.odin.application.topic.TaggingChunks;
import com.e2d.odin.application.topic.TaggingInstructions;
import com.e2d.odin.application.topic.TopicMatcher;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Repite con Odín la medida de T-010 contra el Ollama real (ODIN_TAGGER_URL). Fuera de make check: se lanza con
 * make tagging-battery. Con las claves de control-1 y control-2, que solo dicen si la ficha es de agua, se mide
 * ese tema; con las de control-3, todos.
 */
@Tag("model")
class TaggingBatteryIT {

    private static final Path DIR = Path.of("src/test/resources/tagging-battery");
    private static final Path OUTPUT = Path.of("target/tagging-battery.json");
    private static final String COMPANY_NAME = "Administraciones Ribera";
    private static final String WATER = "agua y humedades";
    private static final int CHUNK_CHARACTERS = 6000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private record Key(String doc, List<String> topics, List<String> acceptable, boolean waterOnly) {
    }

    private record Tagged(Set<String> tags, double seconds, int chunks, int splitChunks) {
    }

    private final List<Topic> topics = loadTopics();
    private TopicTagger tagger;
    private URI baseUrl;

    @Test
    void measuresRecallExtraTagsTimeAndMemory() throws IOException, InterruptedException {
        baseUrl = URI.create(env("ODIN_TAGGER_URL", ""));
        assertThat(baseUrl.toString()).as("falta ODIN_TAGGER_URL").isNotBlank();
        String model = env("ODIN_TAGGER_MODEL", "qwen3.5:9b");
        tagger = new OllamaTopicTagger(baseUrl, model, Duration.ofMinutes(10), optionalInt("ODIN_TAGGER_NUM_GPU"),
                optionalInt("ODIN_TAGGER_NUM_THREAD"), HttpClient.newHttpClient());
        tagger.tag("Responde solo: ok");
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("modelo", model);
        Map<String, Integer> expectedCards = new LinkedHashMap<>();
        for (String set : List.of("control-1", "control-2", "control-3")) {
            List<Key> keys = loadKeys(set);
            expectedCards.put(set, keys.size());
            report.put(set, measureSet(set, keys));
        }
        report.put("documentos_largos", measureLongDocuments());
        report.put("memoria_ollama", ollamaMemory());
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        assertThat(OUTPUT).exists();
        JsonNode written = JSON.readTree(Files.readString(OUTPUT));
        expectedCards.forEach((set, count) -> assertThat(written.get(set).get("fichas").asInt()).isEqualTo(count));
    }

    private Map<String, Object> measureSet(String set, List<Key> keys) throws IOException {
        Map<String, Tagged> tagged = new LinkedHashMap<>();
        for (Key key : keys) {
            tagged.put(key.doc(), tagDocument(pagesOf(DIR.resolve("casos-control").resolve(set).resolve(key.doc()))));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fichas", keys.size());
        result.putAll(recall(keys, tagged));
        result.putAll(extras(keys, tagged));
        double[] seconds = tagged.values().stream().mapToDouble(Tagged::seconds).toArray();
        result.put("segundos_por_documento_medio", round(java.util.Arrays.stream(seconds).average().orElse(0)));
        result.put("segundos_por_documento_max", round(java.util.Arrays.stream(seconds).max().orElse(0)));
        return result;
    }

    private Map<String, Object> recall(List<Key> keys, Map<String, Tagged> tagged) {
        Map<String, Object> byTopic = new LinkedHashMap<>();
        int found = 0;
        int expected = 0;
        for (Topic topic : topics) {
            List<Key> wanting = keys.stream().filter(k -> k.topics().contains(topic.name())).toList();
            long hit = wanting.stream().filter(k -> tagged.get(k.doc()).tags().contains(topic.name())).count();
            if (!wanting.isEmpty()) {
                byTopic.put(topic.name(), Map.of("esperados", wanting.size(), "encontrados", hit, "exhaustividad",
                        percent(hit, wanting.size())));
            }
            found += (int) hit;
            expected += wanting.size();
        }
        return Map.of("exhaustividad_por_tema", byTopic, "exhaustividad_global", percent(found, expected));
    }

    private Map<String, Object> extras(List<Key> keys, Map<String, Tagged> tagged) {
        List<List<String>> extra = new ArrayList<>();
        for (Key key : keys) {
            for (String tag : tagged.get(key.doc()).tags()) {
                boolean judged = !key.waterOnly() || tag.equals(WATER);
                if (judged && !key.topics().contains(tag) && !key.acceptable().contains(tag)) {
                    extra.add(List.of(key.doc(), tag));
                }
            }
        }
        return Map.of("etiquetas_de_mas", extra, "fichas_con_etiqueta_de_mas",
                extra.stream().map(e -> e.get(0)).distinct().count());
    }

    private List<Map<String, Object>> measureLongDocuments() throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Stream<Path> files = Files.walk(DIR.resolve("corpus-limpio"))) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                Tagged tagged = tagDocument(pagesOf(file));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("doc", DIR.resolve("corpus-limpio").relativize(file).toString());
                item.put("tramos", tagged.chunks());
                item.put("tramos_partidos_por_recorte", tagged.splitChunks());
                item.put("temas", tagged.tags());
                item.put("segundos", round(tagged.seconds()));
                result.add(item);
            }
        }
        return result;
    }

    /** Hace lo mismo que TagPendingDocuments: tramos, y un tramo recortado se parte en dos. */
    private Tagged tagDocument(List<DocumentPage> pages) {
        long start = System.nanoTime();
        Set<String> tags = new LinkedHashSet<>();
        int[] counters = new int[2];
        for (String chunk : TaggingChunks.of(pages, CHUNK_CHARACTERS)) {
            counters[0]++;
            tagChunk(chunk, tags, counters);
        }
        return new Tagged(tags, (System.nanoTime() - start) / 1e9, counters[0], counters[1]);
    }

    private void tagChunk(String chunk, Set<String> tags, int[] counters) {
        TaggerAnswer answer = tagger.tag(TaggingInstructions.build(COMPANY_NAME, topics, chunk));
        if (!answer.truncated()) {
            TopicMatcher.match(answer.text(), topics)
                    .forEach(id -> tags.add(topics.stream().filter(t -> t.id().equals(id)).findFirst().get().name()));
        } else if (TaggingChunks.canSplit(chunk)) {
            counters[1]++;
            TaggingChunks.split(chunk).forEach(half -> tagChunk(half, tags, counters));
        } else {
            tags.add("[tramo recortado sin poder partir]");
        }
    }

    private List<Map<String, Object>> ollamaMemory() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.toString().replaceAll("/+$", "") + "/api/ps"))
                .GET().build();
        String body = HttpClient.newHttpClient().send(request, BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        List<Map<String, Object>> models = new ArrayList<>();
        for (JsonNode model : JSON.readTree(body).path("models")) {
            long size = model.path("size").asLong();
            long vram = model.path("size_vram").asLong();
            models.add(Map.of("modelo", model.path("name").asString(), "size_bytes", size, "size_vram_bytes", vram,
                    "en_cpu", vram == 0));
        }
        return models;
    }

    private List<Key> loadKeys(String set) throws IOException {
        List<Key> keys = new ArrayList<>();
        for (JsonNode item : JSON.readTree(Files.readString(DIR.resolve("claves-casos-control").resolve(set + ".json")))) {
            if (item.has("temas")) {
                keys.add(new Key(item.get("doc").asString(), strings(item.get("temas")), strings(item.get("aceptables")),
                        false));
            } else {
                keys.add(new Key(item.get("doc").asString(), item.get("agua").asBoolean() ? List.of(WATER) : List.of(),
                        List.of(), true));
            }
        }
        return keys;
    }

    private static List<Topic> loadTopics() {
        try {
            CompanyId company = new CompanyId(UUID.randomUUID());
            List<Topic> list = new ArrayList<>();
            for (JsonNode item : JSON.readTree(Files.readString(DIR.resolve("temas.json")))) {
                list.add(new Topic(new TopicId(UUID.randomUUID()), company, item.get("name").asString(),
                        item.get("definition").asString()));
            }
            return list;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<DocumentPage> pagesOf(Path file) throws IOException {
        String[] parts = Files.readString(file).split("=== Página \\d+ ===");
        DocumentId id = new DocumentId(UUID.randomUUID());
        List<DocumentPage> pages = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            pages.add(new DocumentPage(id, i, parts[i].strip(), "bateria", "1"));
        }
        return pages;
    }

    private static List<String> strings(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static OptionalInt optionalInt(String name) {
        String value = env(name, "");
        return value.isBlank() ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(value.strip()));
    }

    private static double percent(long part, long total) {
        return total == 0 ? 100.0 : round(100.0 * part / total);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
