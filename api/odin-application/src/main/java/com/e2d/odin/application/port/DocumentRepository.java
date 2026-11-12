package com.e2d.odin.application.port;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository {

    void save(Document document);

    void replacePages(DocumentId id, List<DocumentPage> pages);

    Optional<Document> findById(CompanyScope scope, ProjectVisibility visibility, DocumentId id);

    Optional<DocumentEntry> findEntry(CompanyScope scope, ProjectVisibility visibility, DocumentId id);

    /** Pone la marca de sustituido con su propia orden: {@link #save} no la escribe ni la borra. */
    void markSuperseded(CompanyId companyId, DocumentId id, DocumentId replacementId, Instant at);

    void clearSuperseded(CompanyId companyId, DocumentId id);

    Optional<Document> findBySha256(CompanyId companyId, String sha256);

    List<DocumentEntry> list(CompanyScope scope, ProjectVisibility visibility, Optional<ProjectId> projectId,
            Optional<DocumentStatus> status, int offset, int limit);

    Optional<DocumentPage> findPage(CompanyScope scope, ProjectVisibility visibility, DocumentId id, int pageNumber);

    Optional<Document> claimNextForProcessing(Instant now, Duration staleAfter);
}
