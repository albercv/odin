package com.e2d.odin.adapter.taggerollama;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Los datos de la batería con el modelo real están completos y son coherentes; no necesita el modelo. */
class TaggingBatteryDataTest {

    private static final Path DIR = Path.of("src/test/resources/tagging-battery");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void everyKeyHasItsCardAndOnlyKnownTopics() throws IOException {
        List<String> names = JSON.readTree(Files.readString(DIR.resolve("temas.json"))).valueStream()
                .map(t -> t.get("name").asString()).toList();
        int cards = 0;
        for (String set : List.of("control-1", "control-2", "control-3")) {
            for (JsonNode key : JSON.readTree(Files.readString(DIR.resolve("claves-casos-control/" + set + ".json")))) {
                cards++;
                assertThat(DIR.resolve("casos-control").resolve(set).resolve(key.get("doc").asString())).exists();
                key.path("temas").forEach(t -> assertThat(names).contains(t.asString()));
                key.path("aceptables").forEach(t -> assertThat(names).contains(t.asString()));
            }
        }
        assertThat(cards).isEqualTo(46);
    }

    @Test
    void corpusHasMultiPageDocuments() throws IOException {
        try (var files = Files.walk(DIR.resolve("corpus-limpio"))) {
            List<Path> documents = files.filter(Files::isRegularFile).toList();
            assertThat(documents).isNotEmpty().allSatisfy(f -> assertThat(Files.readString(f)).contains("=== Página 1 ==="));
            assertThat(documents).anySatisfy(f -> assertThat(Files.readString(f)).contains("=== Página 2 ==="));
        }
    }
}
