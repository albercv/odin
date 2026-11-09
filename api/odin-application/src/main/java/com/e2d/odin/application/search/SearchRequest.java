package com.e2d.odin.application.search;

import com.e2d.odin.domain.shared.ProjectId;
import java.util.List;
import java.util.Optional;

public record SearchRequest(List<String> queries, Optional<ProjectId> projectId, Optional<Integer> limit) {
}
