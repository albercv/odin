package com.e2d.odin.domain.knowledge;

import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;

public record CardReview(UserId reviewerId, ReviewDecision decision, Instant reviewedAt, Optional<String> note) {
}
