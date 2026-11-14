package com.e2d.odin.application.intake;

import com.e2d.odin.domain.intake.IntakeProposal;

/** Una propuesta con el título de su documento. */
public record IntakeProposalItem(IntakeProposal proposal, String title) {
}
