package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.application.port.IntakeDocument;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeCounts;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.topic.TopicId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class InMemoryDocumentRepository implements DocumentRepository {

    private final Map<DocumentId, Document> documents = new LinkedHashMap<>();
    private final Map<DocumentId, List<DocumentPage>> pages = new LinkedHashMap<>();
    private CompanyScope lastListScope;
    private BiPredicate<DocumentId, TopicId> activeTopic = (document, topic) -> false;
    private boolean locked;
    private boolean lastMarkWasLocked;

    public boolean lastMarkWasLocked() {
        return lastMarkWasLocked;
    }

    @Override
    public <T> T withDocumentsLocked(CompanyId companyId, DocumentId first, DocumentId second, Supplier<T> work) {
        locked = true;
        try {
            return work.get();
        } finally {
            locked = false;
        }
    }

    public List<DocumentPage> pagesOf(DocumentId id) {
        return pages.getOrDefault(id, List.of());
    }

    /** Todos los documentos, en el orden en que se guardaron la primera vez. */
    public List<Document> all() {
        return List.copyOf(documents.values());
    }

    public Optional<Document> get(DocumentId id) {
        return Optional.ofNullable(documents.get(id));
    }

    /** Quien guarda los temas de los documentos dice cuáles están activos, para el filtro por tema. */
    public void useActiveTopics(BiPredicate<DocumentId, TopicId> predicate) {
        activeTopic = predicate;
    }

    public CompanyScope lastListScope() {
        return lastListScope;
    }

    @Override
    public void save(Document document) {
        Document stored = documents.get(document.id());
        // Como el upsert de PostgreSQL: guardar no escribe ni borra la marca de sustituido ni la de caso (la primera vez sí guarda la de caso).
        documents.put(document.id(), stored == null ? document.withSupersession(Optional.empty(), Optional.empty())
                : document.withSupersession(stored.supersededBy(), stored.supersededAt())
                        .withCaseDocument(stored.caseDocument()));
    }

    @Override
    public void markSuperseded(CompanyId companyId, DocumentId id, DocumentId replacementId, Instant at) {
        lastMarkWasLocked = locked;
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
    public int markCaseDocuments(CompanyId companyId, Set<DocumentId> ids, boolean caseDocument) {
        boolean allOurs = ids.stream().allMatch(id -> documents.containsKey(id)
                && documents.get(id).companyId().equals(companyId));
        if (!allOurs) {
            throw new NotFoundException("Documento no encontrado");
        }
        ids.forEach(id -> documents.put(id, documents.get(id).withCaseDocument(caseDocument)));
        return ids.size();
    }

    @Override
    public void replacePages(DocumentId id, List<DocumentPage> newPages) {
        pages.put(id, List.copyOf(newPages));
    }

    @Override
    public Optional<Document> findById(CompanyScope scope, ProjectVisibility visibility, DocumentId id) {
        return visible(scope, visibility, id).filter(d -> !d.withdrawn() && !d.intakePending());
    }

    private Optional<Document> visible(CompanyScope scope, ProjectVisibility visibility, DocumentId id) {
        return Optional.ofNullable(documents.get(id))
                .filter(d -> scope.includes(d.companyId()) && visibility.allows(d.projectId()));
    }

    @Override
    public Optional<DocumentEntry> findEntry(CompanyScope scope, ProjectVisibility visibility, DocumentId id) {
        return findById(scope, visibility, id).map(d -> entry(scope, visibility, d));
    }

    @Override
    public Optional<DocumentEntry> findEntryIncludingWithdrawn(CompanyScope scope, ProjectVisibility visibility,
            DocumentId id) {
        return visible(scope, visibility, id).map(d -> entry(scope, visibility, d));
    }

    @Override
    public void markWithdrawal(Document document) {
        boolean duplicated = document.withdrawnAt().isEmpty() && documents.values().stream()
                .anyMatch(d -> !d.id().equals(document.id()) && !d.withdrawn()
                        && d.companyId().equals(document.companyId()) && d.projectId().equals(document.projectId())
                        && d.sha256().equals(document.sha256()));
        if (duplicated) {
            throw new ConflictException("Ya hay un documento igual en ese proyecto; retíralo antes de recuperar este");
        }
        documents.computeIfPresent(document.id(),
                (id, stored) -> stored.withWithdrawal(document.withdrawnAt(), document.withdrawnBy()));
    }

    @Override
    public List<DocumentEntry> listWithdrawn(CompanyScope scope, ProjectVisibility visibility, int offset,
            int limit) {
        return documents.values().stream().filter(d -> d.withdrawn() && !d.intakePending() && scope.includes(d.companyId())
                && visibility.allows(d.projectId())).sorted(Comparator.comparing(Document::title).thenComparing(d -> d.id().value()))
                .skip(offset).limit(limit).map(d -> entry(scope, visibility, d)).toList();
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
                .filter(d -> !d.withdrawn() && !d.intakePending() && d.companyId().equals(companyId)
                        && d.sha256().equals(sha256))
                .findFirst();
    }

    @Override
    public Optional<Document> findBySha256ForIntake(CompanyId companyId, IntakeBatchId batchId, String sha256) {
        return documents.values().stream().filter(d -> d.companyId().equals(companyId) && d.sha256().equals(sha256)
                && (!d.withdrawn() || d.intakeBatchId().equals(Optional.of(batchId))))
                .min(Comparator.comparing(Document::withdrawn));
    }

    @Override
    public Set<String> existingSha256ForIntake(CompanyId companyId, IntakeBatchId batchId,
            Collection<String> sha256s) {
        return sha256s.stream().filter(sha -> findBySha256ForIntake(companyId, batchId, sha).isPresent())
                .collect(Collectors.toSet());
    }

    @Override
    public IntakeCounts intakeCounts(CompanyId companyId, IntakeBatchId batchId) {
        List<Document> mine = documents.values().stream().filter(d -> d.companyId().equals(companyId)
                && d.intakeBatchId().equals(Optional.of(batchId)) && !d.withdrawn()).toList();
        long reading = mine.stream().filter(d -> Set.of(DocumentStatus.RECEIVED, DocumentStatus.PROCESSING,
                DocumentStatus.PENDING_READER, DocumentStatus.READER_PROCESSING).contains(d.status())).count();
        return new IntakeCounts(mine.size(), (int) reading, (int) mine.stream().filter(Document::intakePending).count());
    }

    @Override
    public List<String> intakeTexts(CompanyId companyId, IntakeBatchId batchId) {
        return documents.values().stream().filter(d -> d.companyId().equals(companyId)
                && d.intakeBatchId().equals(Optional.of(batchId)) && !d.withdrawn()
                && d.status() == DocumentStatus.READY)
                .map(d -> pagesOf(d.id()).stream().map(DocumentPage::text).collect(java.util.stream.Collectors.joining("\n")))
                .toList();
    }

    @Override
    public List<IntakeDocument> intakeReadyDocuments(CompanyId companyId, IntakeBatchId batchId) {
        return documents.values().stream().filter(d -> d.companyId().equals(companyId)
                && d.intakeBatchId().equals(Optional.of(batchId)) && !d.withdrawn() && d.intakePending()
                && d.status() == DocumentStatus.READY)
                .map(d -> new IntakeDocument(d.id(), d.intakePath(), pagesOf(d.id()).stream()
                        .map(DocumentPage::text).collect(java.util.stream.Collectors.joining("\n")),
                        pagesOf(d.id()).stream().anyMatch(p -> p.readerName().equals("unlimited-ocr-2")))).toList();
    }

    @Override
    public boolean readerQueueBusy(CompanyId companyId) {
        return documents.values().stream().anyMatch(d -> d.companyId().equals(companyId) && !d.withdrawn()
                && (d.status() == DocumentStatus.PENDING_READER || d.status() == DocumentStatus.READER_PROCESSING));
    }

    @Override
    public Optional<Document> findBySha256InProject(CompanyId companyId, Optional<ProjectId> projectId,
            String sha256) {
        return documents.values().stream().filter(d -> !d.withdrawn() && !d.intakePending()
                && d.companyId().equals(companyId) && d.sha256().equals(sha256) && (d.projectId().isEmpty() || d.projectId().equals(projectId)))
                .findFirst();
    }

    @Override
    public List<DocumentEntry> listForAdmin(CompanyScope scope, int offset, int limit) {
        return list(scope, ProjectVisibility.all(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), offset, limit);
    }

    @Override
    public List<DocumentEntry> list(CompanyScope scope, ProjectVisibility visibility, Optional<ProjectId> projectId,
            Optional<TopicId> topicId, Optional<Boolean> caseDocument, Optional<DocumentStatus> status, int offset,
            int limit) {
        lastListScope = scope;
        return documents.values().stream()
                .filter(d -> !d.withdrawn() && !d.intakePending() && scope.includes(d.companyId())
                        && visibility.allows(d.projectId()) && status.map(d.status()::equals).orElse(true))
                .filter(d -> projectId.isEmpty() || d.projectId().equals(projectId))
                .filter(d -> caseDocument.map(flag -> d.caseDocument() == flag).orElse(true))
                .filter(d -> topicId.isEmpty() || activeTopic.test(d.id(), topicId.get()))
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
            if (!d.withdrawn() && (d.status() == DocumentStatus.RECEIVED || stale)) {
                Document claimed = d.startProcessing(now);
                documents.put(claimed.id(), claimed);
                return Optional.of(claimed);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Document> claimNextForReader(Instant now, Duration staleAfter) {
        for (Document d : new ArrayList<>(documents.values())) {
            boolean stale = d.status() == DocumentStatus.READER_PROCESSING
                    && d.processingStartedAt().map(t -> !t.plus(staleAfter).isAfter(now)).orElse(true);
            if (!d.withdrawn() && (d.status() == DocumentStatus.PENDING_READER || stale)) {
                Document claimed = d.startReading(now);
                documents.put(claimed.id(), claimed);
                return Optional.of(claimed);
            }
        }
        return Optional.empty();
    }
}
