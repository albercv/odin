package com.e2d.odin.application.intake;

import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.ProposalConfidence;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.Optional;

/** Filtros opcionales de la bandeja; el proyecto es el propuesto. */
public record ProposalFilter(Optional<ProposalKind> kind, Optional<ProposalConfidence> confidence,
        Optional<ProjectId> project) {

    public static ProposalFilter none() {
        return new ProposalFilter(Optional.empty(), Optional.empty(), Optional.empty());
    }

    boolean matches(IntakeProposal p) {
        return kind.map(k -> k == p.kind()).orElse(true) && confidence.map(c -> c == p.confidence()).orElse(true)
                && project.map(id -> p.targetProjectId().equals(Optional.of(id))).orElse(true);
    }
}
