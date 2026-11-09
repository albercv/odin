package com.e2d.odin.application.port;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository {

    void save(Document document);

    void replacePages(DocumentId id, List<DocumentPage> pages);

    Optional<Document> findById(CompanyScope scope, DocumentId id);

    Optional<Document> findBySha256(CompanyId companyId, String sha256);

    List<Document> list(CompanyScope scope, Optional<ProjectId> projectId, Optional<DocumentStatus> status, int offset,
            int limit);

    Optional<DocumentPage> findPage(CompanyScope scope, DocumentId id, int pageNumber);

    Optional<Document> claimNextForProcessing(Instant now, Duration staleAfter);
}
