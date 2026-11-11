package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryDocumentRepository implements DocumentRepository {

    private final Map<DocumentId, Document> documents = new LinkedHashMap<>();
    private final Map<DocumentId, List<DocumentPage>> pages = new LinkedHashMap<>();
    private CompanyScope lastListScope;

    public List<DocumentPage> pagesOf(DocumentId id) {
        return pages.getOrDefault(id, List.of());
    }

    public Optional<Document> get(DocumentId id) {
        return Optional.ofNullable(documents.get(id));
    }

    public CompanyScope lastListScope() {
        return lastListScope;
    }

    @Override
    public void save(Document document) {
        documents.put(document.id(), document);
    }

    @Override
    public void replacePages(DocumentId id, List<DocumentPage> newPages) {
        pages.put(id, List.copyOf(newPages));
    }

    @Override
    public Optional<Document> findById(CompanyScope scope, DocumentId id) {
        return Optional.ofNullable(documents.get(id)).filter(d -> scope.includes(d.companyId()));
    }

    @Override
    public Optional<Document> findBySha256(CompanyId companyId, String sha256) {
        return documents.values().stream()
                .filter(d -> d.companyId().equals(companyId) && d.sha256().equals(sha256)).findFirst();
    }

    @Override
    public List<Document> list(CompanyScope scope, Optional<ProjectId> projectId, Optional<DocumentStatus> status,
            int offset, int limit) {
        lastListScope = scope;
        return documents.values().stream()
                .filter(d -> scope.includes(d.companyId()) && status.map(d.status()::equals).orElse(true))
                .filter(d -> projectId.isEmpty() || d.projectId().equals(projectId))
                .sorted(Comparator.comparing(Document::title).thenComparing(d -> d.id().value()))
                .skip(offset).limit(limit).toList();
    }

    @Override
    public Optional<DocumentPage> findPage(CompanyScope scope, DocumentId id, int pageNumber) {
        return findById(scope, id).flatMap(d -> pagesOf(id).stream().filter(p -> p.number() == pageNumber).findFirst());
    }

    @Override
    public Optional<Document> claimNextForProcessing(Instant now, Duration staleAfter) {
        List<Document> candidates = new ArrayList<>(documents.values());
        for (Document d : candidates) {
            boolean stale = d.status() == DocumentStatus.PROCESSING
                    && d.processingStartedAt().map(t -> !t.plus(staleAfter).isAfter(now)).orElse(true);
            if (d.status() == DocumentStatus.RECEIVED || stale) {
                Document claimed = d.startProcessing(now);
                documents.put(claimed.id(), claimed);
                return Optional.of(claimed);
            }
        }
        return Optional.empty();
    }
}
