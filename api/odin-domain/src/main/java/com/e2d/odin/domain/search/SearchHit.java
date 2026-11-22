package com.e2d.odin.domain.search;

import com.e2d.odin.domain.context.DocumentContext;
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
        Optional<ReviewStatus> reviewStatus,
        DocumentContext context) {

    public SearchHit(SourceType sourceType, UUID sourceId, String title, Optional<Integer> pageNumber,
            boolean paginated, boolean caseDocument, Optional<ProjectId> projectId, String authorName, Instant date,
            String excerpt, double rank, boolean superseded, Optional<DocumentRef> replacement,
            Optional<ReviewStatus> reviewStatus) {
        this(sourceType, sourceId, title, pageNumber, paginated, caseDocument, projectId, authorName, date, excerpt,
                rank, superseded, replacement, reviewStatus, DocumentContext.NONE);
    }

    public SearchHit withContext(DocumentContext found) {
        return new SearchHit(sourceType, sourceId, title, pageNumber, paginated, caseDocument, projectId, authorName,
                date, excerpt, rank, superseded, replacement, reviewStatus, found);
    }
}
