package com.e2d.odin.bootstrap;

import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.ProcessingSettings;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.application.shared.TaggingWindow;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SettingsConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    InstallationSettings installationSettings(@Value("${odin.setup-token:}") String setupToken,
            @Value("${odin.reader.url:}") String readerUrl) {
        return new InstallationSettings(setupToken, !readerUrl.isBlank());
    }

    @Bean
    UploadSettings uploadSettings(@Value("${odin.upload-max-bytes}") long maxBytes) {
        return new UploadSettings(maxBytes);
    }

    /** La ventana del etiquetado la lee el estado del panel aunque no haya etiquetador. */
    @Bean
    TaggingWindow taggingWindow(@Value("${odin.tagger.window:22:00-07:00}") String window,
            @Value("${odin.timezone:Europe/Madrid}") ZoneId zone) {
        return parseWindow(window, zone);
    }

    /** «always» o «HH:mm-HH:mm»; cualquier otra cosa impide arrancar en vez de etiquetar a horas inesperadas. */
    static TaggingWindow parseWindow(String value, ZoneId zone) {
        if ("always".equalsIgnoreCase(value.strip())) {
            return TaggingWindow.always(zone);
        }
        String[] parts = value.strip().split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("odin.tagger.window debe ser «always» o «HH:mm-HH:mm»: " + value);
        }
        return TaggingWindow.between(LocalTime.parse(parts[0]), LocalTime.parse(parts[1]), zone);
    }

    @Bean
    SessionSettings sessionSettings() {
        return new SessionSettings();
    }

    @Bean
    ProcessingSettings processingSettings() {
        return new ProcessingSettings();
    }
}
