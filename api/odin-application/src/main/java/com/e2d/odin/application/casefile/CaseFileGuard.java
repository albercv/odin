package com.e2d.odin.application.casefile;

import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import java.util.Collection;

public final class CaseFileGuard {

    private CaseFileGuard() {
    }

    /** Desmarcar un caso con documentos en su expediente dejaría relaciones ocultas: se rechaza. */
    public static void requireEmptyCaseFiles(CaseFileRepository caseFiles, CompanyId companyId,
            Collection<DocumentId> caseIds) {
        if (caseIds.stream().anyMatch(id -> caseFiles.countEntries(companyId, id) > 0)) {
            throw new ConflictException("Quita antes los documentos del expediente");
        }
    }
}
