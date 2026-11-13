package com.e2d.odin.application.port;

import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.List;

public interface CaseFileRepository {

    /** Devuelve {@code false} si el documento ya estaba en el expediente. */
    boolean add(CaseFileEntry entry);

    boolean remove(CompanyId companyId, DocumentId caseId, DocumentId documentId);

    void record(CaseFileEvent event);

    /** Solo las relaciones cuyos dos documentos ve la persona. */
    List<CaseFileEntry> entries(CompanyScope scope, ProjectVisibility visibility, DocumentId caseId);

    List<CaseFileEvent> events(CompanyScope scope, ProjectVisibility visibility, DocumentId caseId);

    int countEntries(CompanyId companyId, DocumentId caseId);
}
