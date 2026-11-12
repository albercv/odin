package com.e2d.odin.application.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportMonthTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    // 31 de octubre a las 23:30 UTC ya es 1 de noviembre en Madrid
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-31T23:30:00Z"), ZoneId.of("UTC"));

    @Test
    void defaultsToCurrentMonthInZone() {
        assertThat(ReportMonth.resolve(Optional.empty(), clock, MADRID)).isEqualTo(YearMonth.of(2026, 11));
    }

    @Test
    void acceptsTheLastTwelveMonths() {
        assertThat(ReportMonth.resolve(Optional.of("2025-12"), clock, MADRID)).isEqualTo(YearMonth.of(2025, 12));
        assertThat(ReportMonth.resolve(Optional.of("2026-11"), clock, MADRID)).isEqualTo(YearMonth.of(2026, 11));
    }

    @Test
    void monthOutsideLastTwelveIsRejected() {
        for (String month : new String[] {"2024-01", "2025-11", "2026-12"}) {
            assertThatThrownBy(() -> ReportMonth.resolve(Optional.of(month), clock, MADRID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Solo se pueden consultar los 12 últimos meses");
        }
    }

    @Test
    void malformedMonthIsRejected() {
        for (String month : new String[] {"2026-13", "abc", "2026-1", "", "2026-11-01"}) {
            assertThatThrownBy(() -> ReportMonth.resolve(Optional.of(month), clock, MADRID))
                    .isInstanceOf(ValidationException.class).hasMessage("El mes tiene que tener la forma AAAA-MM");
        }
    }
}
