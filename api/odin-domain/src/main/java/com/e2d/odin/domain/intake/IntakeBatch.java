package com.e2d.odin.domain.intake;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;

/** Máquina de estados del lote: OPEN, READING, IN_REVIEW y CLOSED, siempre en ese orden. */
public record IntakeBatch(IntakeBatchId id, CompanyId companyId, UserId startedBy, IntakeChannel channel,
        IntakeState state, Instant createdAt, Optional<Instant> closedAt) {

    public static IntakeBatch open(IntakeBatchId id, CompanyId companyId, UserId startedBy, IntakeChannel channel,
            Instant now) {
        return new IntakeBatch(id, companyId, startedBy, channel, IntakeState.OPEN, now, Optional.empty());
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

    private IntakeBatch moveTo(IntakeState from, IntakeState to, Optional<Instant> closed) {
        if (state != from) {
            throw new ConflictException("El lote está en " + state + " y no puede pasar a " + to);
        }
        return new IntakeBatch(id, companyId, startedBy, channel, to, createdAt, closed);
    }
}
