package com.e2d.odin.application.shared;

import java.time.Duration;

public record SessionSettings(Duration idleTimeout) {

    public SessionSettings() {
        this(Duration.ofHours(8));
    }
}
