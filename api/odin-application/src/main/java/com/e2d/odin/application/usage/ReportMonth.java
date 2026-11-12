package com.e2d.odin.application.usage;

import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

/** El mes de un informe: el actual por defecto y, como mucho, los 12 últimos. */
public final class ReportMonth {

    private static final int MONTHS_AVAILABLE = 12;
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu-MM")
            .withResolverStyle(ResolverStyle.STRICT);

    private ReportMonth() {
    }

    public static YearMonth resolve(Optional<String> requested, Clock clock, ZoneId zone) {
        YearMonth current = YearMonth.now(clock.withZone(zone));
        if (requested.isEmpty()) {
            return current;
        }
        YearMonth month = parse(requested.get());
        if (month.isAfter(current) || month.isBefore(current.minusMonths(MONTHS_AVAILABLE - 1))) {
            throw new ValidationException("Solo se pueden consultar los 12 últimos meses");
        }
        return month;
    }

    private static YearMonth parse(String value) {
        try {
            return YearMonth.parse(value, FORMAT);
        } catch (DateTimeParseException e) {
            throw new ValidationException("El mes tiene que tener la forma AAAA-MM");
        }
    }
}
