package com.e2d.odin.application.intake;

import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeCounts;
import com.e2d.odin.domain.intake.ProposalKind;
import java.util.List;
import java.util.Map;

/** proposed: propuestas abiertas por clase (con «no lo sé»); toDecide: las que aún no se han decidido; unplaced: documentos pendientes sin propuesta de proyecto por decidir. */
public record IntakeBatchView(IntakeBatch batch, IntakeCounts counts, List<IntakeWarning> warnings,
        Map<ProposalKind, Integer> proposed, int unplaced, int toDecide) {
}
