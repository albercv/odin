package com.e2d.odin.domain.document;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record Document(
        DocumentId id,
        CompanyId companyId,
        Optional<ProjectId> projectId,
        String title,
        String filename,
        DocumentFormat format,
        String sha256,
        DocumentStatus status,
        Optional<String> failureReason,
        boolean paginated,
        UserId uploadedBy,
        Instant uploadedAt,
        Optional<Instant> processingStartedAt,
        int attemptCount,
        Optional<DocumentId> supersededBy,
        Optional<Instant> supersededAt,
        List<Integer> pagesWithoutText,
        boolean caseDocument,
        Optional<Instant> withdrawnAt,
        Optional<UserId> withdrawnBy,
        Optional<IntakeBatchId> intakeBatchId,
        boolean intakePending,
        Optional<String> intakePath,
        Optional<DocumentId> scanOf) {

    /** Tope de saltos al seguir las marcas de sustituido; evita que una cadena circular cuelgue las lecturas. */
    public static final int MAX_SUPERSESSION_CHAIN = 50;

    public Document(DocumentId id, CompanyId companyId, Optional<ProjectId> projectId, String title,
            String filename, DocumentFormat format, String sha256, DocumentStatus status,
            Optional<String> failureReason, boolean paginated, UserId uploadedBy, Instant uploadedAt,
            Optional<Instant> processingStartedAt, int attemptCount, Optional<DocumentId> supersededBy,
            Optional<Instant> supersededAt, List<Integer> pagesWithoutText, boolean caseDocument,
            Optional<Instant> withdrawnAt, Optional<UserId> withdrawnBy) {
        this(id, companyId, projectId, title, filename, format, sha256, status, failureReason, paginated,
                uploadedBy, uploadedAt, processingStartedAt, attemptCount, supersededBy, supersededAt,
                pagesWithoutText, caseDocument, withdrawnAt, withdrawnBy, Optional.empty(), false, Optional.empty(),
                Optional.empty());
    }

    public Document(DocumentId id, CompanyId companyId, Optional<ProjectId> projectId, String title,
            String filename, DocumentFormat format, String sha256, DocumentStatus status,
            Optional<String> failureReason, boolean paginated, UserId uploadedBy, Instant uploadedAt,
            Optional<Instant> processingStartedAt, int attemptCount, Optional<DocumentId> supersededBy,
            Optional<Instant> supersededAt, List<Integer> pagesWithoutText, boolean caseDocument) {
        this(id, companyId, projectId, title, filename, format, sha256, status, failureReason, paginated,
                uploadedBy, uploadedAt, processingStartedAt, attemptCount, supersededBy, supersededAt,
                pagesWithoutText, caseDocument, Optional.empty(), Optional.empty());
    }

    public Document(DocumentId id, CompanyId companyId, Optional<ProjectId> projectId, String title,
            String filename, DocumentFormat format, String sha256, DocumentStatus status,
            Optional<String> failureReason, boolean paginated, UserId uploadedBy, Instant uploadedAt,
            Optional<Instant> processingStartedAt, int attemptCount, Optional<DocumentId> supersededBy,
            Optional<Instant> supersededAt, List<Integer> pagesWithoutText) {
        this(id, companyId, projectId, title, filename, format, sha256, status, failureReason, paginated,
                uploadedBy, uploadedAt, processingStartedAt, attemptCount, supersededBy, supersededAt,
                pagesWithoutText, false, Optional.empty(), Optional.empty());
    }

    public Document(DocumentId id, CompanyId companyId, Optional<ProjectId> projectId, String title,
            String filename, DocumentFormat format, String sha256, DocumentStatus status,
            Optional<String> failureReason, boolean paginated, UserId uploadedBy, Instant uploadedAt,
            Optional<Instant> processingStartedAt, int attemptCount, Optional<DocumentId> supersededBy,
            Optional<Instant> supersededAt) {
        this(id, companyId, projectId, title, filename, format, sha256, status, failureReason, paginated,
                uploadedBy, uploadedAt, processingStartedAt, attemptCount, supersededBy, supersededAt, List.of());
    }

    public Document {
        pagesWithoutText = List.copyOf(pagesWithoutText);
        if (supersededBy.isPresent() != supersededAt.isPresent()) {
            throw new IllegalArgumentException("La marca de sustituido necesita el sustituto y la fecha a la vez");
        }
        if (withdrawnAt.isPresent() != withdrawnBy.isPresent()) {
            throw new IllegalArgumentException("La retirada necesita la persona y la fecha a la vez");
        }
        if (supersededBy.filter(id::equals).isPresent()) {
            throw new IllegalArgumentException("Un documento no puede sustituirse a sí mismo");
        }
    }

    public boolean superseded() {
        return supersededBy.isPresent();
    }

    public boolean withdrawn() {
        return withdrawnAt.isPresent();
    }

    public Document withWithdrawal(Optional<Instant> newWithdrawnAt, Optional<UserId> newWithdrawnBy) {
        return new Document(id, companyId, projectId, title, filename, format, sha256, status, failureReason,
                paginated, uploadedBy, uploadedAt, processingStartedAt, attemptCount, supersededBy, supersededAt,
                pagesWithoutText, caseDocument, newWithdrawnAt, newWithdrawnBy, intakeBatchId, intakePending, intakePath, scanOf);
    }

    public Document withSupersession(Optional<DocumentId> newSupersededBy, Optional<Instant> newSupersededAt) {
        return new Document(id, companyId, projectId, title, filename, format, sha256, status, failureReason,
                paginated, uploadedBy, uploadedAt, processingStartedAt, attemptCount, newSupersededBy,
                newSupersededAt, pagesWithoutText, caseDocument, withdrawnAt, withdrawnBy, intakeBatchId, intakePending, intakePath, scanOf);
    }

    public Document withCaseDocument(boolean newCaseDocument) {
        return new Document(id, companyId, projectId, title, filename, format, sha256, status, failureReason,
                paginated, uploadedBy, uploadedAt, processingStartedAt, attemptCount, supersededBy, supersededAt,
                pagesWithoutText, newCaseDocument, withdrawnAt, withdrawnBy, intakeBatchId, intakePending, intakePath, scanOf);
    }

    /** El documento de una primera carga: pendiente de confirmar, sin proyecto y con la ruta de su carpeta. */
    public Document withIntake(IntakeBatchId batchId, String path) {
        return new Document(id, companyId, Optional.empty(), title, filename, format, sha256, status, failureReason,
                paginated, uploadedBy, uploadedAt, processingStartedAt, attemptCount, supersededBy, supersededAt,
                pagesWithoutText, caseDocument, withdrawnAt, withdrawnBy, Optional.of(batchId), true,
                Optional.of(path), scanOf);
    }

    /** Sale de lo pendiente (D1): queda en ese proyecto, o sin proyecto si es de toda la empresa, y visible. */
    public Document confirmedIn(Optional<ProjectId> newProjectId) {
        return new Document(id, companyId, newProjectId, title, filename, format, sha256, status, failureReason,
                paginated, uploadedBy, uploadedAt, processingStartedAt, attemptCount, supersededBy, supersededAt,
                pagesWithoutText, caseDocument, withdrawnAt, withdrawnBy, intakeBatchId, false, intakePath, scanOf);
    }

    /** Es el escaneo de otro documento (D3). */
    public Document scanOf(DocumentId original) {
        return new Document(id, companyId, projectId, title, filename, format, sha256, status, failureReason,
                paginated, uploadedBy, uploadedAt, processingStartedAt, attemptCount, supersededBy, supersededAt,
                pagesWithoutText, caseDocument, withdrawnAt, withdrawnBy, intakeBatchId, intakePending, intakePath,
                Optional.of(original));
    }

    /** Deja de ser el escaneo de otro: vuelve a ser un documento más. */
    public Document withoutScanOf() {
        return new Document(id, companyId, projectId, title, filename, format, sha256, status, failureReason,
                paginated, uploadedBy, uploadedAt, processingStartedAt, attemptCount, supersededBy, supersededAt,
                pagesWithoutText, caseDocument, withdrawnAt, withdrawnBy, intakeBatchId, intakePending, intakePath,
                Optional.empty());
    }

    public Document startProcessing(Instant now) {
        return copy(DocumentStatus.PROCESSING, Optional.empty(), paginated, Optional.of(now), attemptCount + 1);
    }

    public Document markReady(boolean isPaginated, List<Integer> blankPages) {
        return new Document(id, companyId, projectId, title, filename, format, sha256, DocumentStatus.READY,
                Optional.empty(), isPaginated, uploadedBy, uploadedAt, processingStartedAt, attemptCount,
                supersededBy, supersededAt, blankPages, caseDocument, withdrawnAt, withdrawnBy, intakeBatchId, intakePending, intakePath, scanOf);
    }

    public Document markFailed(String reason) {
        return copy(DocumentStatus.FAILED, Optional.of(reason), paginated, processingStartedAt, attemptCount);
    }

    /** Para el carril local: el lector OCR empieza con el contador a 0, los intentos locales no cuentan. */
    public Document awaitReader() {
        return copy(DocumentStatus.PENDING_READER, Optional.empty(), paginated, processingStartedAt, 0);
    }

    public Document startReading(Instant now) {
        return copy(DocumentStatus.READER_PROCESSING, Optional.empty(), paginated, Optional.of(now),
                attemptCount + 1);
    }

    public Document returnToReaderQueue(boolean countAttempt) {
        return copy(DocumentStatus.PENDING_READER, Optional.empty(), paginated, processingStartedAt,
                countAttempt ? attemptCount : attemptCount - 1);
    }

    public Document requeue() {
        return copy(DocumentStatus.RECEIVED, Optional.empty(), paginated, Optional.empty(), attemptCount);
    }

    public Document resetAttempts() {
        return copy(status, failureReason, paginated, processingStartedAt, 0);
    }

    private Document copy(DocumentStatus newStatus, Optional<String> reason, boolean isPaginated,
            Optional<Instant> startedAt, int attempts) {
        return new Document(id, companyId, projectId, title, filename, format, sha256, newStatus, reason,
                isPaginated, uploadedBy, uploadedAt, startedAt, attempts, supersededBy, supersededAt,
                pagesWithoutText, caseDocument, withdrawnAt, withdrawnBy, intakeBatchId, intakePending, intakePath, scanOf);
    }
}
