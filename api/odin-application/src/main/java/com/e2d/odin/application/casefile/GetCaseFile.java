package com.e2d.odin.application.casefile;

import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import java.util.List;
import java.util.Optional;

/** La ficha de un caso y los documentos de su expediente que ya se pueden leer, por orden de alta en el expediente. */
public class GetCaseFile {

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final DocumentRepository documents;
    private final CaseFileRepository caseFiles;

    public GetCaseFile(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, CaseFileRepository caseFiles) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.documents = documents;
        this.caseFiles = caseFiles;
    }

    public CaseFileView execute(Actor actor, DocumentId caseId) {
        return view(CaseFileAccess.of(actor, Optional.empty(), organizations, projectMembers), caseId);
    }

    /** La ruta del administrador, que nombra la empresa. */
    public CaseFileView execute(Actor actor, CompanyId companyId, DocumentId caseId) {
        return view(CaseFileAccess.of(actor, Optional.of(companyId), organizations, projectMembers), caseId);
    }

    private CaseFileView view(CaseFileAccess access, DocumentId caseId) {
        access.caseDocument(documents, caseId);
        DocumentEntry caseEntry = access.entry(documents, caseId).orElseThrow();
        List<CaseFileDocumentView> readable = caseFiles.entries(access.scope(), access.visibility(), caseId).stream()
                .flatMap(e -> access.entry(documents, e.documentId())
                        .filter(d -> d.document().status() == DocumentStatus.READY)
                        .map(d -> new CaseFileDocumentView(d, e.addedBy(), e.addedAt())).stream())
                .toList();
        return new CaseFileView(caseEntry, readable);
    }
}
