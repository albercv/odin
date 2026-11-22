package com.e2d.odin.application.casefile;

import com.e2d.odin.application.context.DocumentContexts;
import com.e2d.odin.application.document.ScanLinks;
import com.e2d.odin.application.identity.UserNames;
import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.context.DocumentContext;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.UserId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * La ficha de un caso y los documentos de su expediente por orden de alta: el trabajador solo ve los ya legibles y
 * cuántos faltan; el administrador ve también los que aún no lo son, con su estado.
 */
public class GetCaseFile {

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final DocumentRepository documents;
    private final CaseFileRepository caseFiles;
    private final UserNames names;
    private final DocumentContexts contexts;

    public GetCaseFile(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, CaseFileRepository caseFiles, UserRepository users) {
        this(organizations, projectMembers, documents, caseFiles, users, DocumentContexts.none());
    }

    public GetCaseFile(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, CaseFileRepository caseFiles, UserRepository users,
            DocumentContexts contexts) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.documents = documents;
        this.caseFiles = caseFiles;
        this.names = new UserNames(users);
        this.contexts = contexts;
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
        int unreadable = 0;
        for (CaseFileEntry relation : relations) {
            Optional<DocumentEntry> seen = access.entry(documents, relation.documentId())
                    .filter(d -> d.document().withdrawnAt().isEmpty());
            if (seen.isEmpty()) {
                continue;
            }
            DocumentStatus status = seen.get().document().status();
            boolean ready = status == DocumentStatus.READY;
            unreadable += status == DocumentStatus.FAILED ? 1 : 0;
            pending += ready || status == DocumentStatus.FAILED ? 0 : 1;
            if (ready || listUnread) {
                readable.put(relation.documentId(), seen.get());
            }
        }
        Set<DocumentId> toCount = new HashSet<>(readable.keySet());
        toCount.add(caseId);
        Map<DocumentId, Integer> pages = caseFiles.pageCounts(access.scope(), access.visibility(), toCount);
        Map<DocumentId, DocumentContext> context = contexts.of(access.scope(), access.visibility(), toCount);
        List<CaseFileDocumentView> views = views(access, relations, readable, pages, context, listUnread);
        return new CaseFileView(caseEntry, pages.getOrDefault(caseId, 0), views, pending, unreadable,
                names(caseEntry, views), context.getOrDefault(caseId, DocumentContext.NONE));
    }

    private List<CaseFileDocumentView> views(CaseFileAccess access, List<CaseFileEntry> relations,
            Map<DocumentId, DocumentEntry> readable, Map<DocumentId, Integer> pages,
            Map<DocumentId, DocumentContext> context, boolean listUnread) {
        Map<DocumentId, ScanLinks> links = ScanLinks.of(documents, access.scope(), access.visibility(),
                listUnread ? Optional.empty() : Optional.of(DocumentStatus.READY),
                readable.values().stream().map(DocumentEntry::document).toList());
        // Un escaneo cuyo principal está en el expediente sale solo dentro de las copias de su principal.
        return relations.stream().filter(r -> readable.containsKey(r.documentId()))
                .filter(r -> !isScanOfAnother(readable, r.documentId()))
                .map(r -> new CaseFileDocumentView(readable.get(r.documentId()),
                        pages.getOrDefault(r.documentId(), 0), r.addedBy(), r.addedAt(), links.get(r.documentId()),
                        context.getOrDefault(r.documentId(), DocumentContext.NONE)))
                .toList();
    }

    private static boolean isScanOfAnother(Map<DocumentId, DocumentEntry> readable, DocumentId id) {
        return readable.get(id).document().scanOf().filter(readable::containsKey).isPresent();
    }

    private Map<UserId, String> names(DocumentEntry caseEntry, List<CaseFileDocumentView> views) {
        Set<UserId> ids = new HashSet<>(List.of(caseEntry.document().uploadedBy()));
        views.forEach(v -> {
            ids.add(v.document().document().uploadedBy());
            ids.add(v.addedBy());
        });
        return this.names.of(ids);
    }
}
