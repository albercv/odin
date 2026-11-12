package com.e2d.odin.application.shared;

import java.time.Duration;

public record ReaderSettings(Duration readTimeout, int maxAttempts) {

    /** Un documento en lectura más tiempo que el máximo de lectura y 5 minutos se considera abandonado. */
    public Duration staleAfter() {
        return readTimeout.plusMinutes(5);
    }
}
