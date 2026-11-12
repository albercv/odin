package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
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
        Document stored = documents.get(document.id());
        // Como el upsert de PostgreSQL: guardar no escribe ni borra la marca.
        documents.put(document.id(), stored == null ? document.withSupersession(Optional.empty(), Optional.empty())
                : document.withSupersession(stored.supersededBy(), stored.supersededAt()));
    }

    @Override
    public void markSuperseded(CompanyId companyId, DocumentId id, DocumentId replacementId, Instant at) {
        mark(companyId, id, Optional.of(replacementId), Optional.of(at));
    }

    @Override
    public void clearSuperseded(CompanyId companyId, DocumentId id) {
        mark(companyId, id, Optional.empty(), Optional.empty());
    }

    private void mark(CompanyId companyId, DocumentId id, Optional<DocumentId> by, Optional<Instant> at) {
        Document d = documents.get(id);
        if (d != null && d.companyId().equals(companyId)) {
            documents.put(id, d.withSupersession(by, at));
        }
    }

    @Override
    public void replacePages(DocumentId id, List<DocumentPage> newPages) {
        pages.put(id, List.copyOf(newPages));
    }

    @Override
    public Optional<Document> findById(CompanyScope scope, ProjectVisibility visibility, DocumentId id) {
        return Optional.ofNullable(documents.get(id))
                .filter(d -> scope.includes(d.companyId()) && visibility.allows(d.projectId()));
    }

    @Override
    public Optional<DocumentEntry> findEntry(CompanyScope scope, ProjectVisibility visibility, DocumentId id) {
        return findById(scope, visibility, id).map(d -> entry(scope, visibility, d));
    }

    private DocumentEntry entry(CompanyScope scope, ProjectVisibility visibility, Document d) {
        Optional<Document> direct = d.supersededBy().flatMap(by -> findById(scope, visibility, by));
        return new DocumentEntry(d, direct.map(DocumentRef::of),
                direct.flatMap(first -> endOfChain(scope, visibility, first)).map(DocumentRef::of));
    }

    /** El vigente del final de la cadena; vacío si se corta, no termina o pasa del tope, como en PostgreSQL. */
    private Optional<Document> endOfChain(CompanyScope scope, ProjectVisibility visibility, Document first) {
        Document current = first;
        for (int hops = 0; hops < Document.MAX_SUPERSESSION_CHAIN; hops++) {
            if (current.supersededBy().isEmpty()) {
                return Optional.of(current);
            }
            Optional<Document> next = findById(scope, visibility, current.supersededBy().get());
            if (next.isEmpty()) {
                return Optional.empty();
            }
            current = next.get();
        }
        return Optional.empty();
    }

    @Override
    public Optional<Document> findBySha256(CompanyId companyId, String sha256) {
        return documents.values().stream()
                .filter(d -> d.companyId().equals(companyId) && d.sha256().equals(sha256)).findFirst();
    }

    @Override
    public List<DocumentEntry> list(CompanyScope scope, ProjectVisibility visibility, Optional<ProjectId> projectId,
            Optional<DocumentStatus> status, int offset, int limit) {
        lastListScope = scope;
        return documents.values().stream()
                .filter(d -> scope.includes(d.companyId()) && visibility.allows(d.projectId())
                        && status.map(d.status()::equals).orElse(true))
                .filter(d -> projectId.isEmpty() || d.projectId().equals(projectId))
                .sorted(Comparator.comparing(Document::title).thenComparing(d -> d.id().value()))
                .skip(offset).limit(limit).map(d -> entry(scope, visibility, d)).toList();
    }

    @Override
    public Optional<DocumentPage> findPage(CompanyScope scope, ProjectVisibility visibility, DocumentId id,
            int pageNumber) {
        return findById(scope, visibility, id).flatMap(d -> pagesOf(id).stream().filter(p -> p.number() == pageNumber).findFirst());
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
