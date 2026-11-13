package com.e2d.odin.application.document;

import com.e2d.odin.application.casefile.CaseFileGuard;
import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.util.Set;

/** Marca o desmarca un lote de documentos como fichas de caso, todo o nada. */
public class MarkCaseDocuments {

    public static final int MAX_DOCUMENTS = 500;

    private final DocumentRepository documents;
    private final CaseFileRepository caseFiles;
    private final Clock clock;

    public MarkCaseDocuments(DocumentRepository documents, CaseFileRepository caseFiles, Clock clock) {
        this.documents = documents;
        this.caseFiles = caseFiles;
        this.clock = clock;
    }

    public int execute(Actor actor, CompanyId companyId, Set<DocumentId> documentIds, boolean caseDocument) {
        AccessPolicy.requireAdminOf(actor, companyId);
        if (documentIds == null || documentIds.isEmpty() || documentIds.size() > MAX_DOCUMENTS) {
            throw new ValidationException("Elige entre 1 y " + MAX_DOCUMENTS + " documentos");
        }
        if (!caseDocument) {
            CaseFileGuard.requireNoVisibleEntries(caseFiles, companyId, documentIds);
        }
        int updated = documents.markCaseDocuments(companyId, documentIds, caseDocument);
        if (!caseDocument) {
            CaseFileGuard.removeHiddenEntries(caseFiles, companyId, documentIds, actor, clock.instant());
        }
        return updated;
    }
}
