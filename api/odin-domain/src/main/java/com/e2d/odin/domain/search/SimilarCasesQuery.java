package com.e2d.odin.domain.search;

import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Optional;

public record SimilarCasesQuery(List<String> terms, CompanyScope scope, ProjectVisibility visibility,
        Optional<ProjectId> projectId, int limit, UserId searcher) {
}
