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
import java.util.Set;

/** Lo decide una persona, nunca la IA: solo un administrador marca o desmarca un documento como ficha de caso. */
public class UpdateDocument {

    private final DocumentRepository documents;
    private final CaseFileRepository caseFiles;

    public UpdateDocument(DocumentRepository documents, CaseFileRepository caseFiles) {
        this.documents = documents;
        this.caseFiles = caseFiles;
    }

    public DocumentEntry execute(Actor actor, DocumentId documentId, boolean caseDocument) {
        AccessPolicy.requireAdmin(actor);
        Document document = documents.findById(actor.scope(), ProjectVisibility.all(), documentId)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        AccessPolicy.requireAdminOf(actor, document.companyId());
        if (!caseDocument) {
            CaseFileGuard.requireEmptyCaseFiles(caseFiles, document.companyId(), Set.of(documentId));
        }
        documents.markCaseDocuments(document.companyId(), Set.of(documentId), caseDocument);
        return documents.findEntry(CompanyScope.of(document.companyId()), ProjectVisibility.all(), documentId)
                .orElseThrow();
    }
}
