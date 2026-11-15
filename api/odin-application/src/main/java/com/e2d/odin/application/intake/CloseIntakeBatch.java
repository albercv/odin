package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * El administrador cierra la carga (D-113): lo que sigue en «No lo sé» pasa a rechazado, así que sus documentos quedan
 * en «Sin ubicar», y el lote pasa a cerrado. Con propuestas sin decidir no se cierra. Es una transacción.
 */
public class CloseIntakeBatch {

    private final IntakeRepository intake;
    private final Clock clock;

    public CloseIntakeBatch(IntakeRepository intake, Clock clock) {
        this.intake = intake;
        this.clock = clock;
    }

    public IntakeBatch execute(Actor actor, CompanyId companyId, IntakeBatchId batchId) {
        IntakeBatch batch = IntakeBatches.require(intake, actor, companyId, batchId);
        if (batch.state() != IntakeState.IN_REVIEW) {
            throw new ConflictException("La carga solo se puede cerrar cuando está en revisión.");
        }
        List<IntakeProposal> open = intake.proposals(companyId, batchId).stream().filter(IntakeProposal::open).toList();
        long toDecide = open.stream().filter(p -> p.state() == DecisionState.PROPOSED).count();
        if (toDecide > 0) {
            throw new ConflictException((toDecide == 1 ? "Queda 1 propuesta" : "Quedan " + toDecide + " propuestas")
                    + " por decidir. Decídelas antes de cerrar la carga.");
        }
        Instant now = clock.instant();
        open.forEach(p -> intake.save(p.reject(actor.userId(), now)));
        IntakeBatch closed = batch.close(actor.userId(), now);
        intake.save(closed);
        return closed;
    }
}
