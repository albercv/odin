package com.e2d.odin.domain.topic;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import java.time.Instant;
import java.util.Optional;

public record DocumentTagging(DocumentId documentId, CompanyId companyId, int topicListVersion, TaggingStatus status,
        int attemptCount, Optional<Instant> deadlineAt, Optional<String> failureReason,
        Optional<String> taggerVersion) {
}
