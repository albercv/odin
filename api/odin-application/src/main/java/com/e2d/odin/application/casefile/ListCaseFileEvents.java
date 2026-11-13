package com.e2d.odin.application.casefile;

import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
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

    /** Un hecho del historial con el título del documento; {@code null} si el documento está retirado. */
    public record Line(CaseFileEvent event, String documentTitle) {
    }

    public List<Line> execute(Actor actor, CompanyId companyId, DocumentId caseId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        CompanyScope scope = CompanyScope.of(companyId);
        new CaseFileAccess(scope, ProjectVisibility.all()).caseDocument(documents, caseId);
        return caseFiles.events(scope, ProjectVisibility.all(), caseId).stream().map(e -> new Line(e, titleOf(scope, e))).toList();
    }

    private String titleOf(CompanyScope scope, CaseFileEvent event) {
        return documents.findEntryIncludingWithdrawn(scope, ProjectVisibility.all(), event.documentId())
                .map(DocumentEntry::document).filter(d -> !d.withdrawn()).map(Document::title).orElse(null);
    }
}
