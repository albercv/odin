package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** El administrador decide una propuesta: confirmarla, corregir el proyecto, «de toda la empresa», «no lo sé» o rechazarla. */
public class DecideIntakeProposal {

    private final IntakeRepository intake;
    private final IntakeEffects effects;

    public DecideIntakeProposal(IntakeRepository intake, IntakeEffects effects) {
        this.intake = intake;
        this.effects = effects;
    }

    /** Devuelve la propuesta como queda: con «no lo sé» sigue sin decidir. */
    public IntakeProposal execute(Actor actor, CompanyId companyId, IntakeBatchId batchId, UUID proposalId,
            IntakeDecision decision, Optional<ProjectId> projectId) {
        IntakeBatch batch = effects.inReview(actor, companyId, batchId);
        if (decision == null) {
            throw new ValidationException("Indica la decisión");
        }
        List<IntakeProposal> all = intake.proposals(companyId, batchId);
        IntakeProposal p = all.stream().filter(q -> q.id().equals(proposalId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Propuesta no encontrada"));
        IntakeEffects.requireProposed(p);
        switch (decision) {
            case CONFIRM -> effects.confirm(actor, batch, List.of(p), all);
            case CORRECT_PROJECT -> effects.placeAs(actor, batch, p, Optional.of(projectId.orElseThrow(
                    () -> new ValidationException("Indica el proyecto al que va el documento"))), all);
            case COMPANY_WIDE -> effects.placeAs(actor, batch, p, Optional.empty(), all);
            case REJECT -> effects.reject(actor, p);
            case DONT_KNOW -> { }
        }
        effects.closeIfDone(batch);
        return intake.proposals(companyId, batchId).stream().filter(q -> q.id().equals(proposalId)).findFirst()
                .orElseThrow();
    }
}
