package com.e2d.odin.domain.context;

import com.e2d.odin.domain.shared.DocumentId;
import java.util.List;
import java.util.Map;

/** Lo que rodea a un documento: su cadena de versiones, quién lo corrige o a quién corrige, y si falta algo por ver. */
public record DocumentContext(List<DocumentVersion> versions, List<DocumentCorrection> corrections,
        boolean incomplete) {

    public static final DocumentContext NONE = new DocumentContext(List.of(), List.of(), false);

    public DocumentContext {
        versions = List.copyOf(versions);
        corrections = List.copyOf(corrections);
    }

    public boolean corrected() {
        return corrections.stream().anyMatch(c -> c.direction() == CorrectionDirection.CORRECTED_BY);
    }

    public DocumentContext withMatches(Map<DocumentId, PageText> matches) {
        return new DocumentContext(versions.stream()
                .map(v -> v.withMatch(java.util.Optional.ofNullable(matches.get(v.documentId())))).toList(),
                corrections, incomplete);
    }
}
