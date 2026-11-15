package com.e2d.odin.domain.intake;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * Máquina de estados del lote: OPEN, READING, IN_REVIEW y CLOSED, siempre en ese orden. La lista de proyectos se da
 * por terminada una vez, en lectura, con quién y cuándo: solo entonces se clasifica (spec 4.4).
 */
public record IntakeBatch(IntakeBatchId id, CompanyId companyId, UserId startedBy, IntakeChannel channel,
        IntakeState state, Instant createdAt, Optional<Instant> closedAt, int alreadyThere, int discarded,
        Optional<UserId> projectListClosedBy, Optional<Instant> projectListClosedAt) {

    public static IntakeBatch open(IntakeBatchId id, CompanyId companyId, UserId startedBy, IntakeChannel channel,
            Instant now) {
        return new IntakeBatch(id, companyId, startedBy, channel, IntakeState.OPEN, now, Optional.empty(), 0, 0,
                Optional.empty(), Optional.empty());
    }

    public boolean projectListClosed() {
        return projectListClosedAt.isPresent();
    }

    /** Termina la subida: empieza la lectura. */
    public IntakeBatch startReading() {
        return moveTo(IntakeState.OPEN, IntakeState.READING, Optional.empty());
    }

    /** Termina la lectura y la clasificación: queda a la espera de la persona. */
    public IntakeBatch finishReading() {
        return moveTo(IntakeState.READING, IntakeState.IN_REVIEW, Optional.empty());
    }

    public IntakeBatch close(Instant now) {
        return moveTo(IntakeState.IN_REVIEW, IntakeState.CLOSED, Optional.of(now));
    }

    /** «Lista terminada»: el administrador ya no cambia la lista de proyectos y el lote se puede clasificar. */
    public IntakeBatch closeProjectList(UserId by, Instant now) {
        if (state != IntakeState.READING || projectListClosed()) {
            throw new ConflictException("La lista de proyectos ya está terminada o el lote no está en lectura");
        }
        return new IntakeBatch(id, companyId, startedBy, channel, state, createdAt, closedAt, alreadyThere, discarded,
                Optional.of(by), Optional.of(now));
    }

    private IntakeBatch moveTo(IntakeState from, IntakeState to, Optional<Instant> closed) {
        if (state != from) {
            throw new ConflictException("El lote está en " + state + " y no puede pasar a " + to);
        }
        return new IntakeBatch(id, companyId, startedBy, channel, to, createdAt, closed, alreadyThere, discarded,
                projectListClosedBy, projectListClosedAt);
    }
}
