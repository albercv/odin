package com.e2d.odin.bootstrap;

import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.ProcessingSettings;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.application.shared.UploadSettings;
import java.time.Clock;
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

    @Bean
    SessionSettings sessionSettings() {
        return new SessionSettings();
    }

    @Bean
    ProcessingSettings processingSettings() {
        return new ProcessingSettings();
    }
}
