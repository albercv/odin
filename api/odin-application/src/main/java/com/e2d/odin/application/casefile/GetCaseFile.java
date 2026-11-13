package com.e2d.odin.application.casefile;

import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        List<CaseFileEntry> relations = caseFiles.entries(access.scope(), access.visibility(), caseId);
        Map<DocumentId, DocumentEntry> readable = new LinkedHashMap<>();
        for (CaseFileEntry relation : relations) {
            access.entry(documents, relation.documentId()).filter(d -> d.document().status() == DocumentStatus.READY)
                    .ifPresent(d -> readable.put(relation.documentId(), d));
        }
        Set<DocumentId> toCount = new HashSet<>(readable.keySet());
        toCount.add(caseId);
        Map<DocumentId, Integer> pages = caseFiles.pageCounts(access.scope(), access.visibility(), toCount);
        List<CaseFileDocumentView> views = relations.stream().filter(r -> readable.containsKey(r.documentId()))
                .map(r -> new CaseFileDocumentView(readable.get(r.documentId()),
                        pages.getOrDefault(r.documentId(), 0), r.addedBy(), r.addedAt())).toList();
        return new CaseFileView(caseEntry, pages.getOrDefault(caseId, 0), views);
    }
}
