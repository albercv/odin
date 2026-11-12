package com.e2d.odin.domain.document;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
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
        Optional<Instant> supersededAt) {

    /** Tope de saltos al seguir las marcas de sustituido; evita que una cadena circular cuelgue las lecturas. */
    public static final int MAX_SUPERSESSION_CHAIN = 50;

    public Document {
        if (supersededBy.isPresent() != supersededAt.isPresent()) {
            throw new IllegalArgumentException("La marca de sustituido necesita el sustituto y la fecha a la vez");
        }
        if (supersededBy.filter(id::equals).isPresent()) {
            throw new IllegalArgumentException("Un documento no puede sustituirse a sí mismo");
        }
    }

    public boolean superseded() {
        return supersededBy.isPresent();
    }

    public Document withSupersession(Optional<DocumentId> newSupersededBy, Optional<Instant> newSupersededAt) {
        return new Document(id, companyId, projectId, title, filename, format, sha256, status, failureReason,
                paginated, uploadedBy, uploadedAt, processingStartedAt, attemptCount, newSupersededBy,
                newSupersededAt);
    }

    public Document startProcessing(Instant now) {
        return copy(DocumentStatus.PROCESSING, Optional.empty(), paginated, Optional.of(now), attemptCount + 1);
    }

    public Document markReady(boolean isPaginated) {
        return copy(DocumentStatus.READY, Optional.empty(), isPaginated, processingStartedAt, attemptCount);
    }

    public Document markFailed(String reason) {
        return copy(DocumentStatus.FAILED, Optional.of(reason), paginated, processingStartedAt, attemptCount);
    }

    public Document markPendingReader() {
        return copy(DocumentStatus.PENDING_READER, Optional.empty(), paginated, processingStartedAt, attemptCount);
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
                isPaginated, uploadedBy, uploadedAt, startedAt, attempts, supersededBy, supersededAt);
    }
}
