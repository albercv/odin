package com.e2d.odin.domain.casefile;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.UUID;

public record CaseFileEvent(UUID id, CompanyId companyId, DocumentId caseId, DocumentId documentId,
        CaseFileAction action, UserId actorId, Instant at) {
}
