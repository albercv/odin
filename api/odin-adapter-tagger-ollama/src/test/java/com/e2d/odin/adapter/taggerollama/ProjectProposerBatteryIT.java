package com.e2d.odin.adapter.taggerollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.port.ProjectProposer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Los 15 documentos que las reglas dejan sin proyecto en el archivo ficticio (9 normas de la empresa y 6
 * procedimientos), contra el Ollama real (ODIN_TAGGER_URL). Fuera de make check: make project-proposer-battery.
 * En el estudio acertó 14 de 15.
 */
@Tag("model")
class ProjectProposerBatteryIT {

    private static final Path DIR = Path.of("src/test/resources/intake-battery");
    private static final int MINIMUM = 14;

    @Test
    void proposesTheRightProjectForWhatTheRulesLeave() throws IOException {
        String url = System.getenv("ODIN_TAGGER_URL");
        assertThat(url).as("falta ODIN_TAGGER_URL").isNotBlank();
        String model = System.getenv().getOrDefault("ODIN_TAGGER_MODEL", "qwen3.5:9b");
        ProjectProposer proposer = new OllamaProjectProposer(new OllamaTopicTagger(URI.create(url), model,
                Duration.ofMinutes(10), OptionalInt.empty(), OptionalInt.empty(), HttpClient.newHttpClient()));
        List<String> projects = Files.readAllLines(DIR.resolve("projects.txt"));
        List<String> wrong = new ArrayList<>();
        int right = 0;
        for (String line : Files.readAllLines(DIR.resolve("truth.tsv"))) {
            String[] fields = line.split("\t");
            Optional<String> expected = fields[1].equals("ninguno") ? Optional.empty() : Optional.of(fields[1]);
            Optional<String> proposed = proposer.propose(projects, Files.readString(DIR.resolve(fields[0])));
            if (proposed.equals(expected)) {
                right++;
            } else {
                wrong.add(fields[0] + " -> " + proposed.orElse("ninguno"));
            }
        }
        System.out.println("project-proposer-battery: " + right + " de 15; fallos: " + wrong);
        assertThat(right).as("aciertos; fallos: " + wrong).isGreaterThanOrEqualTo(MINIMUM);
    }
}
