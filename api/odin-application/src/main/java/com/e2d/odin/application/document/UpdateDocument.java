package com.e2d.odin.application.document;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.time.Clock;
import java.util.Set;

/** Lo decide una persona, nunca la IA: solo un administrador marca o desmarca un documento como ficha de caso. */
public class UpdateDocument {

    private final DocumentRepository documents;
    private final CaseFileRepository caseFiles;
    private final Clock clock;
    private final AuditLog audit;

    public UpdateDocument(DocumentRepository documents, CaseFileRepository caseFiles, Clock clock,
            AuditLog audit) {
        this.documents = documents;
        this.caseFiles = caseFiles;
        this.clock = clock;
        this.audit = audit;
    }

    public DocumentEntry execute(Actor actor, DocumentId documentId, boolean caseDocument) {
        AccessPolicy.requireAdmin(actor);
        Document document = documents.findById(actor.scope(), ProjectVisibility.all(), documentId)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        AccessPolicy.requireAdminOf(actor, document.companyId());
        if (caseDocument) {
            documents.markCaseDocuments(document.companyId(), Set.of(documentId), true);
        } else {
            caseFiles.unmarkCases(document.companyId(), Set.of(documentId), actor.userId(), clock.instant());
        }
        audit.record(AuditSupport.onDocument(AuditEntry.by(actor, AuditAction.DOCUMENT_CASE_MARK_CHANGED), document)
                .with("caseDocument", caseDocument));
        return documents.findEntry(CompanyScope.of(document.companyId()), ProjectVisibility.all(), documentId)
                .orElseThrow();
    }
}
