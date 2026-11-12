package com.e2d.odin.application.usage;

import java.util.Optional;

/** Recuentos descriptivos de una empresa en un mes. {@code tagging} falta si la empresa no tiene temas. */
public record CompanyUsage(QuestionCounts questions, int questionsWithoutResults, CardCounts cards,
        DocumentCounts documents, Optional<TaggingCounts> tagging) {
}
