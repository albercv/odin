package com.e2d.odin.application.document;

import com.e2d.odin.application.casefile.CaseFileGuard;
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

    public UpdateDocument(DocumentRepository documents, CaseFileRepository caseFiles, Clock clock) {
        this.documents = documents;
        this.caseFiles = caseFiles;
        this.clock = clock;
    }

    public DocumentEntry execute(Actor actor, DocumentId documentId, boolean caseDocument) {
        AccessPolicy.requireAdmin(actor);
        Document document = documents.findById(actor.scope(), ProjectVisibility.all(), documentId)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        AccessPolicy.requireAdminOf(actor, document.companyId());
        if (!caseDocument) {
            CaseFileGuard.requireNoVisibleEntries(caseFiles, document.companyId(), Set.of(documentId));
        }
        documents.markCaseDocuments(document.companyId(), Set.of(documentId), caseDocument);
        if (!caseDocument) {
            CaseFileGuard.removeHiddenEntries(caseFiles, document.companyId(), Set.of(documentId), actor,
                    clock.instant());
        }
        return documents.findEntry(CompanyScope.of(document.companyId()), ProjectVisibility.all(), documentId)
                .orElseThrow();
    }
}
