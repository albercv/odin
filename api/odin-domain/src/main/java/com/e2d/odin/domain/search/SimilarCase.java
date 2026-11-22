package com.e2d.odin.domain.search;

import com.e2d.odin.domain.context.DocumentCorrection;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.knowledge.ReviewStatus;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Un caso parecido. A propósito sin autor: ni quien subió el documento ni quien aportó la ficha (D-040). */
public record SimilarCase(SourceType sourceType, UUID sourceId, String title, Optional<ProjectId> projectId,
        Instant addedAt, int pageCount, List<CasePage> pages, Optional<String> body, boolean truncated,
        String excerpt, boolean superseded, Optional<DocumentRef> replacement,
        Optional<ReviewStatus> reviewStatus, List<DocumentCorrection> corrections) {

    public SimilarCase {
        pages = List.copyOf(pages);
        corrections = List.copyOf(corrections);
    }

    public SimilarCase(SourceType sourceType, UUID sourceId, String title, Optional<ProjectId> projectId,
            Instant addedAt, int pageCount, List<CasePage> pages, Optional<String> body, boolean truncated,
            String excerpt, boolean superseded, Optional<DocumentRef> replacement,
            Optional<ReviewStatus> reviewStatus) {
        this(sourceType, sourceId, title, projectId, addedAt, pageCount, pages, body, truncated, excerpt, superseded,
                replacement, reviewStatus, List.of());
    }

    public SimilarCase withCorrections(List<DocumentCorrection> found) {
        return new SimilarCase(sourceType, sourceId, title, projectId, addedAt, pageCount, pages, body, truncated,
                excerpt, superseded, replacement, reviewStatus, found);
    }
}
