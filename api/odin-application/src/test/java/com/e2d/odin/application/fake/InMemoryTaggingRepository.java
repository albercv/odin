package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.TaggingBacklog;
import com.e2d.odin.application.port.TaggingJob;
import com.e2d.odin.application.port.TaggingRepository;
import com.e2d.odin.application.shared.TaggingSettings;
import com.e2d.odin.application.topic.TaggingChunks;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.topic.DocumentTagging;
import com.e2d.odin.domain.topic.DocumentTopic;
import com.e2d.odin.domain.topic.TaggingStatus;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicSource;
import com.e2d.odin.domain.topic.TopicState;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class InMemoryTaggingRepository implements TaggingRepository {

    private final InMemoryOrganizationRepository orgs;
    private final InMemoryDocumentRepository docs;
    private final InMemoryTopicRepository topics;
    private final Map<DocumentId, DocumentTagging> rows = new LinkedHashMap<>();
    private final Map<DocumentId, Map<TopicId, DocumentTopic>> documentTopics = new LinkedHashMap<>();

    public InMemoryTaggingRepository(InMemoryOrganizationRepository orgs, InMemoryDocumentRepository docs,
            InMemoryTopicRepository topics) {
        this.orgs = orgs;
        this.docs = docs;
        this.topics = topics;
        docs.useActiveTopics((document, topic) -> documentTopics.getOrDefault(document, Map.of()).values().stream()
                .anyMatch(t -> t.topicId().equals(topic) && t.state() == TopicState.ACTIVE));
    }

    /** Un tema ya guardado de un documento, sin pasar por el etiquetador ni por una decisión. */
    public void seed(DocumentId documentId, TopicId topicId, TopicSource source, TopicState state) {
        documentTopics.computeIfAbsent(documentId, k -> new LinkedHashMap<>()).put(topicId,
                new DocumentTopic(documentId, topicId, source, state,
                        source == TopicSource.AI ? Optional.of("modelo; instrucciones v1") : Optional.empty(),
                        Optional.empty(), Optional.empty()));
    }

    /** El estado del etiquetado de un documento con la versión actual de la lista. */
    public void seedRow(Document d, TaggingStatus status) {
        rows.put(d.id(), new DocumentTagging(d.id(), d.companyId(), topics.listVersion(d.companyId()), status, 1,
                Optional.empty(), Optional.empty(), Optional.empty()));
    }

    public Optional<DocumentTagging> rowOf(DocumentId id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public Optional<TaggingJob> claimNext(Instant now, TaggingSettings settings) {
        return docs.all().stream().filter(d -> claimable(d, now))
                .min(Comparator.comparingInt(this::group)).map(d -> claim(d, now, settings));
    }

    private boolean claimable(Document d, Instant now) {
        if (d.status() != DocumentStatus.READY || topics.list(d.companyId()).isEmpty()) {
            return false;
        }
        DocumentTagging row = rows.get(d.id());
        if (row == null) {
            return true;
        }
        boolean outdated = row.topicListVersion() < topics.listVersion(d.companyId());
        boolean expired = row.status() == TaggingStatus.TAGGING && row.deadlineAt().map(now::isAfter).orElse(true);
        return row.status() == TaggingStatus.TAGGING ? expired
                : row.status() == TaggingStatus.PENDING || outdated;
    }

    /** 0: nunca etiquetado o con la versión actual; 1: de una versión anterior. La lista conserva el orden de subida. */
    private int group(Document d) {
        DocumentTagging row = rows.get(d.id());
        return row == null || row.topicListVersion() == topics.listVersion(d.companyId()) ? 0 : 1;
    }

    private TaggingJob claim(Document d, Instant now, TaggingSettings settings) {
        int version = topics.listVersion(d.companyId());
        DocumentTagging old = rows.get(d.id());
        int attempts = old == null || old.topicListVersion() < version ? 1 : old.attemptCount() + 1;
        var pages = docs.pagesOf(d.id());
        int chunks = Math.max(1, TaggingChunks.of(pages, settings.chunkCharacters()).size());
        Instant deadline = now.plus(settings.chunkTimeout().multipliedBy(chunks)).plusSeconds(300);
        rows.put(d.id(), new DocumentTagging(d.id(), d.companyId(), version, TaggingStatus.TAGGING, attempts,
                Optional.of(deadline), Optional.empty(), Optional.empty()));
        String name = orgs.findCompany(d.companyId()).orElseThrow().name();
        return new TaggingJob(d, pages, topics.list(d.companyId()), name, version, attempts);
    }

    @Override
    public void saveResult(DocumentId documentId, Set<TopicId> aiTopics, String taggerVersion, Instant now) {
        DocumentTagging row = rows.get(documentId);
        if (row == null || row.status() != TaggingStatus.TAGGING) {
            return;
        }
        Map<TopicId, DocumentTopic> current = documentTopics.computeIfAbsent(documentId, k -> new LinkedHashMap<>());
        current.values().removeIf(t -> t.source() == TopicSource.AI && t.decidedBy().isEmpty());
        Document owner = docs.get(documentId).orElseThrow();
        for (TopicId topicId : aiTopics) {
            if (topics.find(owner.companyId(), topicId).isEmpty()) {
                continue;
            }
            current.putIfAbsent(topicId, new DocumentTopic(documentId, topicId, TopicSource.AI, TopicState.ACTIVE,
                    Optional.of(taggerVersion), Optional.empty(), Optional.empty()));
        }
        rows.put(documentId, withStatus(row, TaggingStatus.DONE, row.attemptCount(), Optional.empty(),
                Optional.of(taggerVersion)));
    }

    @Override
    public void markFailed(DocumentId documentId, String reason) {
        DocumentTagging row = rows.get(documentId);
        if (row != null) {
            rows.put(documentId, new DocumentTagging(documentId, row.companyId(), row.topicListVersion(),
                    TaggingStatus.FAILED, row.attemptCount(), Optional.empty(), Optional.of(reason), Optional.empty()));
        }
    }

    @Override
    public void returnToQueue(DocumentId documentId, boolean countAttempt) {
        DocumentTagging row = rows.get(documentId);
        if (row != null) {
            rows.put(documentId, withStatus(row, TaggingStatus.PENDING,
                    countAttempt ? row.attemptCount() : Math.max(0, row.attemptCount() - 1), Optional.empty(),
                    Optional.empty()));
        }
    }

    private static DocumentTagging withStatus(DocumentTagging row, TaggingStatus status, int attempts,
            Optional<String> reason, Optional<String> version) {
        return new DocumentTagging(row.documentId(), row.companyId(), row.topicListVersion(), status, attempts,
                Optional.empty(), reason, version);
    }

    @Override
    public void retag(CompanyScope scope, DocumentId documentId) {
        docs.get(documentId).filter(d -> scope.includes(d.companyId())).ifPresent(d -> rows.remove(documentId));
    }

    @Override
    public List<DocumentTopic> topicsOf(CompanyScope scope, DocumentId documentId) {
        return docs.get(documentId).filter(d -> scope.includes(d.companyId()))
                .map(d -> documentTopics.getOrDefault(documentId, Map.of()).values().stream()
                        .filter(t -> topics.find(d.companyId(), t.topicId()).isPresent()).toList())
                .orElse(List.of());
    }

    @Override
    public Map<DocumentId, List<DocumentTopic>> activeTopicsOf(CompanyScope scope, ProjectVisibility visibility,
            Collection<DocumentId> documentIds) {
        Map<DocumentId, List<DocumentTopic>> found = new LinkedHashMap<>();
        for (DocumentId id : documentIds) {
            List<DocumentTopic> active = docs.findById(scope, visibility, id).map(d -> documentTopics
                    .getOrDefault(id, Map.of()).values().stream().filter(t -> t.state() == TopicState.ACTIVE)
                    .filter(t -> topics.find(d.companyId(), t.topicId()).isPresent()).toList()).orElse(List.of());
            if (!active.isEmpty()) {
                found.put(id, active);
            }
        }
        return found;
    }

    @Override
    public Map<DocumentId, DocumentTagging> taggingOf(CompanyScope scope, ProjectVisibility visibility,
            Collection<DocumentId> documentIds) {
        Map<DocumentId, DocumentTagging> found = new LinkedHashMap<>();
        documentIds.stream().filter(id -> docs.findById(scope, visibility, id).isPresent())
                .filter(rows::containsKey).forEach(id -> found.put(id, rows.get(id)));
        return found;
    }

    @Override
    public void decide(CompanyScope scope, DocumentId documentId, TopicId topicId, TopicState state, UserId by,
            Instant at) {
        Document d = docs.get(documentId).filter(doc -> scope.includes(doc.companyId())).orElse(null);
        if (d == null || topics.find(d.companyId(), topicId).isEmpty()) {
            return;
        }
        Map<TopicId, DocumentTopic> current = documentTopics.computeIfAbsent(documentId, k -> new LinkedHashMap<>());
        DocumentTopic old = current.get(topicId);
        boolean keepsSource = state == TopicState.REMOVED && old != null;
        current.put(topicId, new DocumentTopic(documentId, topicId, keepsSource ? old.source() : TopicSource.ADMIN,
                state, keepsSource ? old.taggerVersion() : Optional.empty(), Optional.of(by), Optional.of(at)));
    }

    @Override
    public int pendingCount(CompanyId companyId, ProjectVisibility visibility) {
        return (int) pending(companyId).filter(d -> visibility.allows(d.projectId())).count();
    }

    @Override
    public TaggingBacklog backlog(CompanyId companyId) {
        List<Document> pending = pending(companyId).toList();
        Optional<Instant> oldest = pending.stream().map(Document::uploadedAt).min(Comparator.naturalOrder());
        return new TaggingBacklog(pending.size(), oldest);
    }

    private java.util.stream.Stream<Document> pending(CompanyId companyId) {
        int version = topics.listVersion(companyId);
        return docs.all().stream().filter(d -> d.companyId().equals(companyId) && d.status() == DocumentStatus.READY
                && !topics.list(companyId).isEmpty() && !isDone(d.id(), version));
    }

    private boolean isDone(DocumentId id, int version) {
        DocumentTagging row = rows.get(id);
        return row != null && row.status() == TaggingStatus.DONE && row.topicListVersion() == version;
    }
}
