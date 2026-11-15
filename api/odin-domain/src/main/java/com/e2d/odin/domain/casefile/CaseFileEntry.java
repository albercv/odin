package com.e2d.odin.domain.casefile;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;

public record CaseFileEntry(CompanyId companyId, DocumentId caseId, DocumentId documentId, UserId addedBy,
        Instant addedAt) {
}
