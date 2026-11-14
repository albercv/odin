package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.NotFoundException;

/** Lo común de los casos de uso del lote: el administrador de la empresa y un lote que es de esa empresa. */
final class IntakeBatches {

    private IntakeBatches() {
    }

    static IntakeBatch require(IntakeRepository intake, Actor actor, CompanyId companyId, IntakeBatchId batchId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        return intake.find(companyId, batchId).orElseThrow(() -> new NotFoundException("Lote no encontrado"));
    }

    /** Importar, descubrir y decidir candidatos: solo mientras la lista no esté terminada ni el lote clasificado. */
    static IntakeBatch requireListOpen(IntakeRepository intake, Actor actor, CompanyId companyId,
            IntakeBatchId batchId) {
        IntakeBatch batch = require(intake, actor, companyId, batchId);
        if (batch.projectListClosed() || batch.state() == IntakeState.IN_REVIEW
                || batch.state() == IntakeState.CLOSED) {
            throw new ConflictException("La lista de proyectos ya está terminada: no se puede cambiar");
        }
        return batch;
    }
}
