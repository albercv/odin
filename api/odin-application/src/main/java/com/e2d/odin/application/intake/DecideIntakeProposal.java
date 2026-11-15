package com.e2d.odin.application.intake;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
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
    private final AuditLog audit;

    public DecideIntakeProposal(IntakeRepository intake, IntakeEffects effects, AuditLog audit) {
        this.intake = intake;
        this.effects = effects;
        this.audit = audit;
    }

    /** Devuelve la propuesta como queda: con «no lo sé», DONT_KNOW con quién y cuándo, y se puede decidir después. */
    public IntakeProposal execute(Actor actor, CompanyId companyId, IntakeBatchId batchId, UUID proposalId,
            IntakeDecision decision, Optional<ProjectId> projectId) {
        IntakeBatch batch = effects.inReview(actor, companyId, batchId);
        if (decision == null) {
            throw new ValidationException("Indica la decisión");
        }
        List<IntakeProposal> all = intake.proposals(companyId, batchId);
        IntakeProposal p = all.stream().filter(q -> q.id().equals(proposalId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Propuesta no encontrada"));
        IntakeEffects.requireOpen(p);
        switch (decision) {
            case CONFIRM -> effects.confirm(actor, batch, List.of(p), all);
            case CORRECT_PROJECT -> effects.placeAs(actor, batch, p, Optional.of(projectId.orElseThrow(
                    () -> new ValidationException("Indica el proyecto al que va el documento"))), all);
            case COMPANY_WIDE -> effects.placeAs(actor, batch, p, Optional.empty(), all);
            case REJECT -> effects.reject(actor, p);
            case DONT_KNOW -> effects.dontKnow(actor, p);
        }
        effects.closeIfDone(batch);
        AuditEntry entry = AuditSupport.onIntake(AuditEntry.by(actor, AuditAction.INTAKE_PROPOSAL_DECIDED), companyId,
                batchId).with("proposalId", proposalId.toString()).with("decision", decision.name());
        audit.record(projectId.map(entry::onProject).orElse(entry));
        return intake.proposals(companyId, batchId).stream().filter(q -> q.id().equals(proposalId)).findFirst()
                .orElseThrow();
    }
}
