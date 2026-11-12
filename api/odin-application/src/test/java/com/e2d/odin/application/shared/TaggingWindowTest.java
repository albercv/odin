package com.e2d.odin.application.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TaggingWindowTest {

    private static final TaggingWindow NIGHT = TaggingWindow.between(LocalTime.of(22, 0), LocalTime.of(7, 0),
            ZoneOffset.UTC);

    @Test
    void nightWindowCrossesMidnight() {
        assertThat(NIGHT.isOpen(Instant.parse("2026-11-09T22:00:00Z"))).isTrue();
        assertThat(NIGHT.isOpen(Instant.parse("2026-11-09T23:59:00Z"))).isTrue();
        assertThat(NIGHT.isOpen(Instant.parse("2026-11-10T03:00:00Z"))).isTrue();
        assertThat(NIGHT.isOpen(Instant.parse("2026-11-10T06:59:00Z"))).isTrue();
        assertThat(NIGHT.isOpen(Instant.parse("2026-11-10T07:00:00Z"))).isFalse();
        assertThat(NIGHT.isOpen(Instant.parse("2026-11-10T12:00:00Z"))).isFalse();
        assertThat(NIGHT.isOpen(Instant.parse("2026-11-10T21:59:00Z"))).isFalse();
    }

    @Test
    void dayWindowDoesNotCrossMidnight() {
        TaggingWindow day = TaggingWindow.between(LocalTime.of(9, 0), LocalTime.of(17, 0), ZoneOffset.UTC);

        assertThat(day.isOpen(Instant.parse("2026-11-10T09:00:00Z"))).isTrue();
        assertThat(day.isOpen(Instant.parse("2026-11-10T17:00:00Z"))).isFalse();
        assertThat(day.isOpen(Instant.parse("2026-11-10T03:00:00Z"))).isFalse();
    }

    @Test
    void alwaysIsOpen() {
        TaggingWindow always = TaggingWindow.always(ZoneOffset.UTC);

        assertThat(always.isOpen(Instant.parse("2026-11-10T12:00:00Z"))).isTrue();
        assertThat(always.isOpen(Instant.parse("2026-11-10T03:00:00Z"))).isTrue();
    }

    @Test
    void usesInstallationZone() {
        TaggingWindow madrid = TaggingWindow.between(LocalTime.of(22, 0), LocalTime.of(7, 0),
                ZoneId.of("Europe/Madrid"));

        // 23:30 UTC del 9 de noviembre son las 00:30 del 10 en Madrid (UTC+1): dentro de la ventana.
        assertThat(madrid.isOpen(Instant.parse("2026-11-09T23:30:00Z"))).isTrue();
        // 21:30 UTC son las 22:30 en Madrid: dentro, aunque en UTC aún no habría empezado.
        assertThat(madrid.isOpen(Instant.parse("2026-11-09T21:30:00Z"))).isTrue();
        // 12:00 UTC son las 13:00 en Madrid: fuera.
        assertThat(madrid.isOpen(Instant.parse("2026-11-09T12:00:00Z"))).isFalse();
        // 06:30 UTC son las 07:30 en Madrid: fuera, aunque en UTC aún sería de noche.
        assertThat(madrid.isOpen(Instant.parse("2026-11-10T06:30:00Z"))).isFalse();
    }
}
