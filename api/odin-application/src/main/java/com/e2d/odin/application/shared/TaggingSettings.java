package com.e2d.odin.application.shared;

import java.time.Duration;

public record TaggingSettings(TaggingWindow window, Duration chunkTimeout, int chunkCharacters, int maxAttempts) {
}
