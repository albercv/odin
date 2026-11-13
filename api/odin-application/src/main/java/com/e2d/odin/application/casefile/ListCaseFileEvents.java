package com.e2d.odin.application.casefile;

import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.List;

/** La línea de tiempo del expediente, solo para el administrador. */
public class ListCaseFileEvents {

    private final DocumentRepository documents;
    private final CaseFileRepository caseFiles;

    public ListCaseFileEvents(DocumentRepository documents, CaseFileRepository caseFiles) {
        this.documents = documents;
        this.caseFiles = caseFiles;
    }

    public List<CaseFileEvent> execute(Actor actor, CompanyId companyId, DocumentId caseId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        CompanyScope scope = CompanyScope.of(companyId);
        documents.findById(scope, ProjectVisibility.all(), caseId)
                .orElseThrow(() -> new NotFoundException("Caso no encontrado"));
        return caseFiles.events(scope, ProjectVisibility.all(), caseId);
    }
}
