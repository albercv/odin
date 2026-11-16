package com.e2d.odin.application.document;

import com.e2d.odin.application.port.AuditLog;
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
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class UploadDocument {

    private final DocumentRepository documents;
    private final OrganizationRepository organizations;
    private final OriginalFileStore files;
    private final DocumentFormatDetector detector;
    private final UploadSettings settings;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog audit;

    public UploadDocument(DocumentRepository documents, OrganizationRepository organizations, OriginalFileStore files,
            DocumentFormatDetector detector, UploadSettings settings, IdGenerator ids, Clock clock,
            AuditLog audit) {
        this.documents = documents;
        this.organizations = organizations;
        this.files = files;
        this.detector = detector;
        this.settings = settings;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
    }

    public UploadOutcome execute(Actor actor, CompanyId companyId, Optional<ProjectId> projectId, String title,
            String filename, byte[] content, boolean caseDocument) {
        AccessPolicy.requireAdminOf(actor, companyId);
        organizations.findCompany(companyId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        projectId.ifPresent(id -> organizations.findProject(companyId, id)
                .orElseThrow(() -> new NotFoundException("Proyecto no encontrado")));
        UploadOutcome outcome = store(actor, companyId, projectId, title, filename, content, caseDocument,
                sha256 -> documents.findBySha256(companyId, sha256), documents::save, UnaryOperator.identity());
        audit.record(outcome.auditEntry(actor, content.length));
        return outcome;
    }

    /**
     * Sin entrada de registro: la deja quien llama (AddFileToIntake). La subida de una primera carga: el documento nace pendiente, sin proyecto y con su ruta. La huella se busca en la
     * empresa y, aunque esté retirada, en el propio lote, para que reanudar no devuelva lo que se descartó.
     */
    public UploadOutcome executeForIntake(Actor actor, CompanyId companyId, IntakeBatchId batchId, String path,
            String title, String filename, byte[] content) {
        AccessPolicy.requireAdminOf(actor, companyId);
        organizations.findCompany(companyId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        return store(actor, companyId, Optional.empty(), title, filename, content, false,
                sha256 -> documents.findBySha256ForIntake(companyId, batchId, sha256), documents::save,
                d -> d.withIntake(batchId, path));
    }

    /**
     * Sin entrada de registro: la deja quien llama (el administrador en UploadToCaseFile.executeAsAdmin, el enlace en
     * su propio caso de uso). La subida a un expediente: quien llama ya comprobó que la persona puede, y los duplicados se buscan solo en el
     * proyecto del caso y entre lo general, sin delatar lo que haya en otros proyectos. Quien llama decide cómo se
     * guarda un documento nuevo ({@code persist}), para hacerlo junto con su entrada en el expediente.
     */
    public UploadOutcome executeForCase(Actor actor, CompanyId companyId, Optional<ProjectId> projectId,
            String title, String filename, byte[] content, Consumer<Document> persist) {
        return store(actor, companyId, projectId, title, filename, content, false,
                sha256 -> documents.findBySha256InProject(companyId, projectId, sha256), persist,
                UnaryOperator.identity());
    }

    private UploadOutcome store(Actor actor, CompanyId companyId, Optional<ProjectId> projectId, String title,
            String filename, byte[] content, boolean caseDocument, Function<String, Optional<Document>> duplicate,
            Consumer<Document> persist, UnaryOperator<Document> shape) {
        String cleanTitle = Validation.requireText(title, "el título");
        String cleanFilename = Validation.requireText(filename, "el nombre del fichero");
        DocumentFormat format = detectFormat(content);
        String sha256 = files.store(content);
        Optional<Document> existing = duplicate.apply(sha256);
        if (existing.isPresent()) {
            return new UploadOutcome(requeueIfFailed(existing.get()), true);
        }
        Document document = shape.apply(new Document(new DocumentId(ids.newId()), companyId, projectId, cleanTitle,
                cleanFilename, format, sha256, DocumentStatus.RECEIVED, Optional.empty(), false, actor.userId(),
                clock.instant(), Optional.empty(), 0, Optional.empty(), Optional.empty(), List.of(), caseDocument));
        return persistOrLinkTheOneThatWon(document, duplicate, persist);
    }

    /** Si otra subida idéntica ganó el índice único por un instante, se enlaza el documento que creó. */
    private UploadOutcome persistOrLinkTheOneThatWon(Document document, Function<String, Optional<Document>> duplicate,
            Consumer<Document> persist) {
        try {
            persist.accept(document);
        } catch (ConflictException e) {
            Document winner = duplicate.apply(document.sha256()).orElseThrow(() -> e);
            return new UploadOutcome(requeueIfFailed(winner), true);
        }
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
        if (existing.status() == DocumentStatus.FAILED && !existing.withdrawn()) {
            documents.save(existing.requeue().resetAttempts());
        }
        // también lo pendiente y lo retirado: la subida de una primera carga puede devolver cualquiera de los dos
        return documents.findEntryIncludingWithdrawn(CompanyScope.of(existing.companyId()), ProjectVisibility.all(),
                existing.id()).orElseThrow();
    }
}
