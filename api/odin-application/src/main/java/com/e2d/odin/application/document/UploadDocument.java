package com.e2d.odin.application.document;

import com.e2d.odin.application.port.DocumentFormatDetector;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.OriginalFileStore;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.PayloadTooLargeException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UnsupportedFormatException;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class UploadDocument {

    private final DocumentRepository documents;
    private final OrganizationRepository organizations;
    private final OriginalFileStore files;
    private final DocumentFormatDetector detector;
    private final UploadSettings settings;
    private final IdGenerator ids;
    private final Clock clock;

    public UploadDocument(DocumentRepository documents, OrganizationRepository organizations, OriginalFileStore files,
            DocumentFormatDetector detector, UploadSettings settings, IdGenerator ids, Clock clock) {
        this.documents = documents;
        this.organizations = organizations;
        this.files = files;
        this.detector = detector;
        this.settings = settings;
        this.ids = ids;
        this.clock = clock;
    }

    public UploadOutcome execute(Actor actor, CompanyId companyId, Optional<ProjectId> projectId, String title,
            String filename, byte[] content, boolean caseDocument) {
        AccessPolicy.requireAdminOf(actor, companyId);
        organizations.findCompany(companyId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        projectId.ifPresent(id -> organizations.findProject(companyId, id)
                .orElseThrow(() -> new NotFoundException("Proyecto no encontrado")));
        return store(actor, companyId, projectId, title, filename, content, caseDocument,
                sha256 -> documents.findBySha256(companyId, sha256));
    }

    /**
     * La subida por enlace: la persona ya la autorizó el enlace, el título es el nombre del fichero y los duplicados
     * se buscan solo en el proyecto y entre lo general, sin delatar lo que haya en otros proyectos.
     */
    public UploadOutcome executeForLink(Actor actor, CompanyId companyId, Optional<ProjectId> projectId,
            String filename, byte[] content) {
        return store(actor, companyId, projectId, filename, filename, content, false,
                sha256 -> documents.findBySha256InProject(companyId, projectId, sha256));
    }

    private UploadOutcome store(Actor actor, CompanyId companyId, Optional<ProjectId> projectId, String title,
            String filename, byte[] content, boolean caseDocument, Function<String, Optional<Document>> duplicate) {
        String cleanTitle = Validation.requireText(title, "el título");
        String cleanFilename = Validation.requireText(filename, "el nombre del fichero");
        DocumentFormat format = detectFormat(content);
        String sha256 = files.store(content);
        Optional<Document> existing = duplicate.apply(sha256);
        if (existing.isPresent()) {
            return new UploadOutcome(requeueIfFailed(existing.get()), true);
        }
        Document document = new Document(new DocumentId(ids.newId()), companyId, projectId, cleanTitle, cleanFilename,
                format, sha256, DocumentStatus.RECEIVED, Optional.empty(), false, actor.userId(), clock.instant(),
                Optional.empty(), 0, Optional.empty(), Optional.empty(), List.of(), caseDocument);
        documents.save(document);
        return new UploadOutcome(new DocumentEntry(document, Optional.empty(), Optional.empty()), false);
    }

    private DocumentFormat detectFormat(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ValidationException("El fichero está vacío");
        }
        if (content.length > settings.maxBytes()) {
            throw new PayloadTooLargeException("El fichero supera el tamaño máximo permitido");
        }
        return detector.detect(content).orElseThrow(() -> new UnsupportedFormatException("Formato no admitido"));
    }

    private DocumentEntry requeueIfFailed(Document existing) {
        if (existing.status() == DocumentStatus.FAILED) {
            documents.save(existing.requeue().resetAttempts());
        }
        return documents.findEntry(CompanyScope.of(existing.companyId()), ProjectVisibility.all(), existing.id())
                .orElseThrow();
    }
}
