package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import java.time.Clock;

/**
 * «Lista terminada»: el administrador da por buena la lista de proyectos y el lote se puede clasificar (spec 4.4). Solo
 * con todo leído, el descubrimiento hecho sobre los textos leídos y ningún candidato por decidir; vale con ninguno
 * confirmado (entonces no hay propuestas de proyecto). Sin transacción que lo envuelva: lo descubierto se queda aunque
 * falten candidatos por decidir, para que el administrador los vea.
 */
public class CloseProjectList {

    private final IntakeRepository intake;
    private final DocumentRepository documents;
    private final ListProjectCandidates candidates;
    private final Clock clock;

    public CloseProjectList(IntakeRepository intake, DocumentRepository documents, ListProjectCandidates candidates,
            Clock clock) {
        this.intake = intake;
        this.documents = documents;
        this.candidates = candidates;
        this.clock = clock;
    }

    public IntakeBatch execute(Actor actor, CompanyId companyId, IntakeBatchId batchId) {
        IntakeBatch batch = IntakeBatches.requireListOpen(intake, actor, companyId, batchId);
        if (batch.state() != IntakeState.READING) {
            throw new ConflictException("La lista se termina cuando la subida está cerrada");
        }
        int reading = documents.intakeCounts(companyId, batchId).reading();
        if (reading > 0) {
            throw new ConflictException(reading == 1 ? "Aún se está leyendo 1 documento"
                    : "Aún se están leyendo " + reading + " documentos");
        }
        long undecided = candidates.discover(actor, companyId, batchId).stream()
                .filter(c -> c.state() == DecisionState.PROPOSED).count();
        if (undecided > 0) {
            throw new ConflictException(undecided == 1 ? "Falta 1 candidato por decidir"
                    : "Faltan " + undecided + " candidatos por decidir");
        }
        IntakeBatch closed = batch.closeProjectList(actor.userId(), clock.instant());
        intake.save(closed);
        return closed;
    }
}
