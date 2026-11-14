package com.e2d.odin.application.intake;

import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeCounts;
import java.util.List;

public record IntakeBatchView(IntakeBatch batch, IntakeCounts counts, List<IntakeWarning> warnings) {
}
