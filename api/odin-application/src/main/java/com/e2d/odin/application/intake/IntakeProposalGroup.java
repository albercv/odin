package com.e2d.odin.application.intake;

import com.e2d.odin.domain.intake.ProposalConfidence;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.List;
import java.util.Optional;

/** Propuestas por decidir del mismo tipo, confianza y proyecto propuesto (vacío en las que no lo tienen). */
public record IntakeProposalGroup(ProposalKind kind, ProposalConfidence confidence, Optional<ProjectId> projectId,
        List<IntakeProposalItem> items) {
}
