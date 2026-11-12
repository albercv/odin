package com.e2d.odin.bootstrap;

import com.e2d.odin.adapter.taggerollama.OllamaTopicTagger;
import com.e2d.odin.application.port.TaggingRepository;
import com.e2d.odin.application.port.TopicTagger;
import com.e2d.odin.application.shared.ProcessingSettings;
import com.e2d.odin.application.shared.TaggingSettings;
import com.e2d.odin.application.shared.TaggingWindow;
import com.e2d.odin.application.topic.TagPendingDocuments;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.OptionalInt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** El carril del etiquetado existe solo si odin.tagger.url tiene valor. */
@Configuration
@ConditionalOnExpression("!'${odin.tagger.url:}'.isBlank()")
public class TaggingConfiguration {

    @Bean
    TopicTagger topicTagger(@Value("${odin.tagger.url}") URI url,
            @Value("${odin.tagger.model:qwen3.5:9b}") String model,
            @Value("${odin.tagger.timeout:PT5M}") Duration timeout,
            @Value("${odin.tagger.num-gpu:}") String numGpu, @Value("${odin.tagger.num-thread:}") String numThread) {
        return new OllamaTopicTagger(url, model, timeout, optionalInt(numGpu), optionalInt(numThread),
                HttpClient.newHttpClient());
    }

    @Bean
    TaggingSettings taggingSettings(@Value("${odin.tagger.window:22:00-07:00}") String window,
            @Value("${odin.timezone:Europe/Madrid}") ZoneId zone,
            @Value("${odin.tagger.timeout:PT5M}") Duration timeout,
            @Value("${odin.tagger.chunk-characters:6000}") int chunkCharacters, ProcessingSettings processing) {
        return new TaggingSettings(window(window, zone), timeout, chunkCharacters, processing.maxAttempts());
    }

    @Bean
    TagPendingDocuments tagPendingDocuments(TaggingRepository tagging, TopicTagger tagger, TaggingSettings settings,
            Clock clock) {
        return new TagPendingDocuments(tagging, tagger, settings, clock);
    }

    @Bean
    TaggingScheduler taggingScheduler(TagPendingDocuments tag) {
        return new TaggingScheduler(tag);
    }

    /** «always» o «HH:mm-HH:mm»; cualquier otra cosa impide arrancar en vez de etiquetar a horas inesperadas. */
    static TaggingWindow window(String value, ZoneId zone) {
        if ("always".equalsIgnoreCase(value.strip())) {
            return TaggingWindow.always(zone);
        }
        String[] parts = value.strip().split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("odin.tagger.window debe ser «always» o «HH:mm-HH:mm»: " + value);
        }
        return TaggingWindow.between(LocalTime.parse(parts[0]), LocalTime.parse(parts[1]), zone);
    }

    private static OptionalInt optionalInt(String value) {
        return value.isBlank() ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(value.strip()));
    }
}
