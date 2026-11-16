package com.e2d.odin.application.shared;

import java.time.Duration;

/** Pausa entre reintentos: min(base × 2^(intentos − 1), tope), contada desde el fallo. */
public record RetryPause(Duration base, Duration cap) {

    public static final RetryPause NONE = new RetryPause(Duration.ZERO, Duration.ZERO);
}
