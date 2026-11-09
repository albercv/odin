package com.e2d.odin.application.shared;

import java.time.Duration;

public record ProcessingSettings(Duration staleAfter, int maxAttempts) {

    public ProcessingSettings() {
        this(Duration.ofMinutes(15), 3);
    }
}
