package com.e2d.odin.domain.search;

import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.knowledge.ReviewStatus;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record SearchHit(
        SourceType sourceType,
        UUID sourceId,
        String title,
        Optional<Integer> pageNumber,
        boolean paginated,
        boolean caseDocument,
        Optional<ProjectId> projectId,
        String authorName,
        Instant date,
        String excerpt,
        double rank,
        boolean superseded,
        Optional<DocumentRef> replacement,
        Optional<ReviewStatus> reviewStatus) {
}
