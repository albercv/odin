package com.e2d.odin.application.document;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
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
    private final AuditLog audit;

    public MarkCaseDocuments(DocumentRepository documents, CaseFileRepository caseFiles, Clock clock,
            AuditLog audit) {
        this.documents = documents;
        this.caseFiles = caseFiles;
        this.clock = clock;
        this.audit = audit;
    }

    public int execute(Actor actor, CompanyId companyId, Set<DocumentId> documentIds, boolean caseDocument) {
        AccessPolicy.requireAdminOf(actor, companyId);
        if (documentIds == null || documentIds.isEmpty() || documentIds.size() > MAX_DOCUMENTS) {
            throw new ValidationException("Elige entre 1 y " + MAX_DOCUMENTS + " documentos");
        }
        int changed = caseDocument ? documents.markCaseDocuments(companyId, documentIds, true)
                : caseFiles.unmarkCases(companyId, documentIds, actor.userId(), clock.instant());
        audit.record(AuditSupport.withIds(AuditEntry.by(actor, AuditAction.DOCUMENTS_CASE_MARK_CHANGED)
                .onCompany(companyId), documentIds.stream().map(id -> id.value().toString()).sorted().toList())
                .with("caseDocument", caseDocument));
        return changed;
    }
}
