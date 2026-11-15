package com.e2d.odin.domain.intake;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.Optional;

public record ProjectCandidate(CompanyId companyId, IntakeBatchId batchId, String name, CandidateOrigin origin,
        int documentCount, DecisionState state, Optional<ProjectId> projectId) {

    public ProjectCandidate confirm(ProjectId project) {
        requireProposed();
        return new ProjectCandidate(companyId, batchId, name, origin, documentCount, DecisionState.CONFIRMED,
                Optional.of(project));
    }

    public ProjectCandidate reject() {
        requireProposed();
        return new ProjectCandidate(companyId, batchId, name, origin, documentCount, DecisionState.REJECTED,
                Optional.empty());
    }

    private void requireProposed() {
        if (state != DecisionState.PROPOSED) {
            throw new ConflictException("El candidato ya está decidido");
        }
    }
}
