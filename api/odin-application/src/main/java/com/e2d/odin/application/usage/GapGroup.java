package com.e2d.odin.application.usage;

import com.e2d.odin.domain.knowledge.QuestionKind;
import java.time.LocalDate;

/** Una consulta sin resultados, agrupada por texto. Sin autor y con el día, sin la hora. */
public record GapGroup(String text, QuestionKind kind, int times, LocalDate lastAskedOn) {
}
