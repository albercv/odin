package com.e2d.odin.application.casefile;

import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.UserId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * La ficha de un caso y los documentos de su expediente por orden de alta: el trabajador solo ve los ya legibles y
 * cuántos faltan; el administrador ve también los que aún no lo son, con su estado.
 */
public class GetCaseFile {

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final DocumentRepository documents;
    private final CaseFileRepository caseFiles;
    private final UserRepository users;

    public GetCaseFile(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, CaseFileRepository caseFiles, UserRepository users) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.documents = documents;
        this.caseFiles = caseFiles;
        this.users = users;
    }

    public CaseFileView execute(Actor actor, DocumentId caseId) {
        return view(CaseFileAccess.of(actor, Optional.empty(), organizations, projectMembers), caseId, false);
    }

    /** La ruta del administrador, que nombra la empresa. */
    public CaseFileView execute(Actor actor, CompanyId companyId, DocumentId caseId) {
        return view(CaseFileAccess.of(actor, Optional.of(companyId), organizations, projectMembers), caseId, true);
    }

    private CaseFileView view(CaseFileAccess access, DocumentId caseId, boolean listUnread) {
        access.caseDocument(documents, caseId);
        DocumentEntry caseEntry = access.entry(documents, caseId).orElseThrow();
        List<CaseFileEntry> relations = caseFiles.entries(access.scope(), access.visibility(), caseId);
        Map<DocumentId, DocumentEntry> readable = new LinkedHashMap<>();
        int pending = 0;
        for (CaseFileEntry relation : relations) {
            Optional<DocumentEntry> seen = access.entry(documents, relation.documentId())
                    .filter(d -> d.document().withdrawnAt().isEmpty());
            if (seen.isEmpty()) {
                continue;
            }
            boolean ready = seen.get().document().status() == DocumentStatus.READY;
            pending += ready ? 0 : 1;
            if (ready || listUnread) {
                readable.put(relation.documentId(), seen.get());
            }
        }
        Set<DocumentId> toCount = new HashSet<>(readable.keySet());
        toCount.add(caseId);
        Map<DocumentId, Integer> pages = caseFiles.pageCounts(access.scope(), access.visibility(), toCount);
        List<CaseFileDocumentView> views = relations.stream().filter(r -> readable.containsKey(r.documentId()))
                .map(r -> new CaseFileDocumentView(readable.get(r.documentId()),
                        pages.getOrDefault(r.documentId(), 0), r.addedBy(), r.addedAt())).toList();
        return new CaseFileView(caseEntry, pages.getOrDefault(caseId, 0), views, pending, names(caseEntry, views));
    }

    private Map<UserId, String> names(DocumentEntry caseEntry, List<CaseFileDocumentView> views) {
        Set<UserId> ids = new HashSet<>(List.of(caseEntry.document().uploadedBy()));
        views.forEach(v -> {
            ids.add(v.document().document().uploadedBy());
            ids.add(v.addedBy());
        });
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::id, User::displayName));
    }
}
