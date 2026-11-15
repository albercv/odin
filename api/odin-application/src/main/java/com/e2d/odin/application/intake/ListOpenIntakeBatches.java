package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.List;

/** Los lotes de la empresa que aún no están cerrados, el más reciente primero: el panel abre el primero. */
public class ListOpenIntakeBatches {

    private final IntakeRepository intake;

    public ListOpenIntakeBatches(IntakeRepository intake) {
        this.intake = intake;
    }

    public List<IntakeBatch> execute(Actor actor, CompanyId companyId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        return intake.findOpen(companyId);
    }
}
