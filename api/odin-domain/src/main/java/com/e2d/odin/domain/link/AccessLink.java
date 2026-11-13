package com.e2d.odin.domain.link;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record AccessLink(UUID id, AccessLinkKind kind, CompanyId companyId, UserId userId, Optional<DocumentId> caseId,
        Optional<DocumentId> documentId, Instant createdAt, Instant expiresAt, Optional<String> boundAddress,
        int uploadCount) {
}
