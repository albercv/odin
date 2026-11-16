package com.e2d.odin.application.intake;

import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;

/**
 * El administrador coloca un documento que ninguna propuesta resuelve: en un proyecto o de toda la empresa. Mismos
 * efectos que corregir el proyecto o «de toda la empresa» sobre una propuesta, sin propuesta.
 */
public class PlaceIntakeDocument {

    private final IntakeEffects effects;

    public PlaceIntakeDocument(IntakeEffects effects) {
        this.effects = effects;
    }

    public void execute(Actor actor, CompanyId companyId, IntakeBatchId batchId, DocumentId documentId,
            Optional<ProjectId> projectId, boolean companyWide) {
        IntakeBatch batch = effects.unplacedEditable(actor, companyId, batchId);
        if (projectId.isPresent() == companyWide) {
            throw new ValidationException("Indica un proyecto o «de toda la empresa», solo uno de los dos");
        }
        effects.placeUnplaced(actor, batch, documentId, projectId);
        effects.closeIfDone(batch);
    }
}
