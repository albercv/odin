package com.e2d.odin.application.usage;

import java.util.List;

/** Los huecos del mes, o ninguno con {@code withheld} si los preguntaron muy pocas personas. */
public record GapReport(List<GapGroup> groups, boolean withheld) {
}
