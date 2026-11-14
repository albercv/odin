package com.e2d.odin.application.casefile;

import com.e2d.odin.domain.shared.DocumentId;
import java.util.Set;

/** Un caso y los documentos que se añaden a su expediente. */
public record CaseFileAssignment(DocumentId caseId, Set<DocumentId> documentIds) {

    public CaseFileAssignment {
        documentIds = Set.copyOf(documentIds);
    }
}
