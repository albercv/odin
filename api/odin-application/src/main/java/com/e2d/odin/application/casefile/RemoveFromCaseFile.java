package com.e2d.odin.application.casefile;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.domain.casefile.CaseFileAction;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import java.time.Clock;
import java.util.Optional;

/** Quita un documento del expediente de un caso; el documento sigue en Odín. */
public class RemoveFromCaseFile {

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final DocumentRepository documents;
    private final CaseFileRepository caseFiles;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog audit;

    public RemoveFromCaseFile(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, CaseFileRepository caseFiles, IdGenerator ids, Clock clock,
            AuditLog audit) {
        this.audit = audit;
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.documents = documents;
        this.caseFiles = caseFiles;
        this.ids = ids;
        this.clock = clock;
    }

    public void execute(Actor actor, DocumentId caseId, DocumentId documentId) {
        remove(actor, CaseFileAccess.forWrite(actor, Optional.empty(), organizations, projectMembers), caseId, documentId);
    }

    /** La ruta del administrador, que nombra la empresa: deja una entrada. */
    public void execute(Actor actor, CompanyId companyId, DocumentId caseId, DocumentId documentId) {
        Document caseDocument = remove(actor,
                CaseFileAccess.of(actor, Optional.of(companyId), organizations, projectMembers), caseId, documentId);
        audit.record(AuditSupport.onCaseFile(AuditEntry.by(actor, AuditAction.CASE_FILE_DOCUMENT_REMOVED),
                caseDocument).with("documentId", documentId.value().toString()));
    }

    private Document remove(Actor actor, CaseFileAccess access, DocumentId caseId, DocumentId documentId) {
        Document caseDocument = access.caseDocument(documents, caseId);
        access.document(documents, documentId);
        boolean removed = caseFiles.removeAndRecord(new CaseFileEvent(ids.newId(), caseDocument.companyId(), caseId,
                documentId, CaseFileAction.REMOVED, actor.userId(), clock.instant()));
        if (!removed) {
            throw new NotFoundException("Documento no encontrado");
        }
        return caseDocument;
    }
}
