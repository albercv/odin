package com.e2d.odin.domain.knowledge;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.UUID;

public record QuestionRecord(UUID id, CompanyId companyId, UserId authorId, String text, int hitCount, Instant askedAt) {
}
