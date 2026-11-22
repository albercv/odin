package com.e2d.odin.domain.context;

import com.e2d.odin.domain.citation.LinkRelation;
import com.e2d.odin.domain.document.DocumentRef;
import java.util.Optional;

/**
 * Un vínculo de corrección con otro documento. Si el otro no es visible para quien lee, {@code document} va vacío,
 * {@code hidden} es verdadero y no hay texto del documento oculto en {@code evidence}.
 */
public record DocumentCorrection(CorrectionDirection direction, LinkRelation relation, Optional<DocumentRef> document,
        boolean hidden, Optional<PageText> evidence, boolean confirmed) {
}
