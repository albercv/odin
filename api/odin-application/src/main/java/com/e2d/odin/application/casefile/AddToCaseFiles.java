package com.e2d.odin.application.casefile;

import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.domain.casefile.CaseFileAction;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Añade documentos que ya están en Odín a los expedientes de sus casos, todo o nada. */
public class AddToCaseFiles {

    public static final int MAX_PAIRS = 500;

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final DocumentRepository documents;
    private final CaseFileRepository caseFiles;
    private final IdGenerator ids;
    private final Clock clock;

    public AddToCaseFiles(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, CaseFileRepository caseFiles, IdGenerator ids, Clock clock) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.documents = documents;
        this.caseFiles = caseFiles;
        this.ids = ids;
        this.clock = clock;
    }

    public int execute(Actor actor, List<CaseFileAssignment> assignments) {
        return add(actor, CaseFileAccess.of(actor, Optional.empty(), organizations, projectMembers), assignments);
    }

    /** La ruta del administrador, que nombra la empresa. */
    public int execute(Actor actor, CompanyId companyId, List<CaseFileAssignment> assignments) {
        return add(actor, CaseFileAccess.of(actor, Optional.of(companyId), organizations, projectMembers),
                assignments);
    }

    /** Antes de guardar un documento para un caso: el caso existe, lo es, y el documento irá en su proyecto o en lo general. */
    public void requireTarget(Actor actor, CompanyId companyId, DocumentId caseId, Optional<ProjectId> projectId) {
        Document caseDocument = CaseFileAccess.of(actor, Optional.of(companyId), organizations, projectMembers)
                .caseDocument(documents, caseId);
        if (projectId.isPresent() && !projectId.equals(caseDocument.projectId())) {
            throw new NotFoundException("Caso no encontrado");
        }
    }

    private int add(Actor actor, CaseFileAccess access, List<CaseFileAssignment> assignments) {
        Instant now = clock.instant();
        Set<CaseFileEntry> entries = new LinkedHashSet<>();
        for (CaseFileAssignment assignment : assignments == null ? List.<CaseFileAssignment>of() : assignments) {
            Document caseDocument = access.caseDocument(documents, assignment.caseId());
            for (var documentId : assignment.documentIds()) {
                Document document = access.document(documents, documentId);
                requireBelongs(caseDocument, document);
                entries.add(new CaseFileEntry(caseDocument.companyId(), caseDocument.id(), document.id(),
                        actor.userId(), now));
            }
        }
        if (entries.isEmpty() || entries.size() > MAX_PAIRS) {
            throw new ValidationException("Elige entre 1 y " + MAX_PAIRS + " documentos");
        }
        return caseFiles.addAll(List.copyOf(entries), entry -> new CaseFileEvent(ids.newId(), entry.companyId(),
                entry.caseId(), entry.documentId(), CaseFileAction.ADDED, actor.userId(), now));
    }

    private static void requireBelongs(Document caseDocument, Document document) {
        if (document.id().equals(caseDocument.id())) {
            throw new ValidationException("La ficha del caso no se añade a su propio expediente");
        }
        boolean general = document.projectId().isEmpty();
        if (!general && !document.projectId().equals(caseDocument.projectId())) {
            throw new NotFoundException("Documento no encontrado");
        }
    }
}
