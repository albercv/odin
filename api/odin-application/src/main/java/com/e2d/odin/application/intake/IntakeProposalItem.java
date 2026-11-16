package com.e2d.odin.application.intake;

import com.e2d.odin.domain.intake.IntakeProposal;
import java.util.Optional;

/** Una propuesta con el tipo de su documento (solo para la bandeja), con el título de su documento y, si tiene otro documento (pareja o ficha), el título de ese. */
public record IntakeProposalItem(IntakeProposal proposal, String title, Optional<String> targetTitle,
        Optional<String> documentType) {
}
