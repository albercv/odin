package com.e2d.odin.application.search;

import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.List;
import java.util.Optional;

public record SearchRequest(List<String> queries, Optional<ProjectId> projectId, Optional<Integer> limit,
        Optional<DocumentId> caseId, boolean general) {

    public SearchRequest(List<String> queries, Optional<ProjectId> projectId, Optional<Integer> limit,
            Optional<DocumentId> caseId) {
        this(queries, projectId, limit, caseId, false);
    }

    public SearchRequest(List<String> queries, Optional<ProjectId> projectId, Optional<Integer> limit) {
        this(queries, projectId, limit, Optional.empty());
    }
}
