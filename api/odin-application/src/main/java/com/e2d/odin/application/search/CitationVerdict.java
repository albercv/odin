package com.e2d.odin.application.search;

import com.e2d.odin.domain.context.DocumentCorrection;
import com.e2d.odin.domain.context.CorrectionDirection;
import com.e2d.odin.domain.document.DocumentRef;
import java.util.List;
import java.util.Optional;

/** Si la cita está en el documento y si ese documento está sustituido (con su vigente) o corregido por otro. */
public record CitationVerdict(boolean verified, boolean superseded, Optional<DocumentRef> replacement,
        List<DocumentCorrection> corrections) {

    public static final CitationVerdict UNVERIFIED = new CitationVerdict(false, false, Optional.empty(), List.of());

    public boolean corrected() {
        return corrections.stream().anyMatch(c -> c.direction() == CorrectionDirection.CORRECTED_BY);
    }
}
