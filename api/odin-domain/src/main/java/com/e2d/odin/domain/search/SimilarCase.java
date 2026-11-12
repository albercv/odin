package com.e2d.odin.domain.search;

import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Un caso parecido. A propósito sin autor: ni quien subió el documento ni quien aportó la ficha (D-040). */
public record SimilarCase(SourceType sourceType, UUID sourceId, String title, Optional<ProjectId> projectId,
        Instant addedAt, int pageCount, List<CasePage> pages, Optional<String> body, boolean truncated,
        String excerpt, boolean superseded, Optional<DocumentRef> replacement) {

    public SimilarCase {
        pages = List.copyOf(pages);
    }
}
