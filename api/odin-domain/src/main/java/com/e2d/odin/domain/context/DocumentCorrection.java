package com.e2d.odin.domain.context;

import com.e2d.odin.domain.citation.LinkRelation;
import com.e2d.odin.domain.document.DocumentRef;
import java.util.Optional;

/**
 * Un vínculo de corrección con otro documento. Si el otro no es visible para quien lee, {@code document}, {@code
 * evidence} y {@code confirmed} van vacíos y {@code hidden} es verdadero.
 */
public record DocumentCorrection(CorrectionDirection direction, LinkRelation relation, Optional<DocumentRef> document,
        boolean hidden, Optional<PageText> evidence, Optional<Boolean> confirmed) {

    /** Un vínculo cuyo otro extremo no se ve: solo se sabe que existe, de qué clase y en qué sentido. */
    public static DocumentCorrection hiddenOf(CorrectionDirection direction, LinkRelation relation) {
        return new DocumentCorrection(direction, relation, Optional.empty(), true, Optional.empty(), Optional.empty());
    }
}
