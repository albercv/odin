package com.e2d.odin.application.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.port.DocumentContextRepository;
import com.e2d.odin.domain.context.DocumentContext;
import com.e2d.odin.domain.context.DocumentCorrection;
import com.e2d.odin.domain.context.DocumentVersion;
import com.e2d.odin.domain.context.PageText;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SourceType;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentContextsTest {

    private final Map<DocumentId, DocumentContext> contexts = new HashMap<>();
    private final DocumentContexts service = new DocumentContexts(new DocumentContextRepository() {
        @Override
        public Map<DocumentId, DocumentContext> contexts(CompanyScope scope, ProjectVisibility visibility,
                Collection<DocumentId> ids) {
            Map<DocumentId, DocumentContext> found = new HashMap<>();
            ids.stream().filter(contexts::containsKey).forEach(id -> found.put(id, contexts.get(id)));
            return found;
        }

        @Override
        public Map<DocumentId, List<DocumentCorrection>> corrections(CompanyScope scope, ProjectVisibility visibility,
                Collection<DocumentId> ids) {
            return Map.of();
        }

        @Override
        public Map<DocumentId, PageText> bestMatches(CompanyScope scope, ProjectVisibility visibility,
                List<String> terms, Collection<DocumentId> ids) {
            Map<DocumentId, PageText> found = new HashMap<>();
            ids.forEach(id -> found.put(id, new PageText(2, "mejor página de " + id.value())));
            return found;
        }
    });

    private SearchHit hit(DocumentId id, String title) {
        return new SearchHit(SourceType.ORIGINAL_DOCUMENT, id.value(), title, Optional.of(1), true, false,
                Optional.empty(), "Ana", Instant.EPOCH, "x", 1.0, false, Optional.empty(), Optional.empty());
    }

    private List<DocumentId> chain(String... titles) {
        List<DocumentId> ids = java.util.stream.IntStream.range(0, titles.length)
                .mapToObj(i -> new DocumentId(UUID.randomUUID())).toList();
        List<DocumentVersion> versions = java.util.stream.IntStream.range(0, titles.length)
                .mapToObj(i -> new DocumentVersion(ids.get(i), titles[i], i == titles.length - 1, Optional.empty(),
                        Optional.empty(), Optional.empty())).toList();
        ids.forEach(id -> contexts.put(id, new DocumentContext(versions, List.of(), false)));
        return ids;
    }

    private List<SearchHit> run(List<SearchHit> candidates, int limit) {
        return service.forSearch(CompanyScope.of(new com.e2d.odin.domain.shared.CompanyId(UUID.randomUUID())),
                ProjectVisibility.all(), List.of("q"), candidates, limit);
    }

    @Test
    void versionsOfTheSameChainCollapseIntoTheNewestAndFreeRoomForOthers() {
        List<DocumentId> policy = chain("v1", "v2", "v3");
        DocumentId other1 = new DocumentId(UUID.randomUUID());
        DocumentId other2 = new DocumentId(UUID.randomUUID());
        List<SearchHit> candidates = List.of(hit(policy.get(1), "v2"), hit(other1, "otro1"), hit(policy.get(2), "v3"),
                hit(policy.get(0), "v1"), hit(other2, "otro2"));

        List<SearchHit> result = run(candidates, 3);

        assertThat(result).extracting(SearchHit::title).containsExactly("otro1", "v3", "otro2");
        SearchHit grouped = result.get(1);
        assertThat(grouped.context().versions()).extracting(DocumentVersion::title).containsExactly("v1", "v2", "v3");
        assertThat(grouped.context().versions()).allSatisfy(v -> assertThat(v.match()).isPresent());
    }

    @Test
    void ifOnlyOldVersionsComeOutTheMostRecentOfThemKeepsTheWholeChain() {
        List<DocumentId> policy = chain("v1", "v2", "v3");

        List<SearchHit> result = run(List.of(hit(policy.get(0), "v1"), hit(policy.get(1), "v2")), 10);

        assertThat(result).extracting(SearchHit::title).containsExactly("v2");
        assertThat(result.get(0).context().versions()).hasSize(3);
    }

    @Test
    void cardsAndDocumentsWithoutChainAreLeftAlone() {
        DocumentId plain = new DocumentId(UUID.randomUUID());
        SearchHit card = new SearchHit(SourceType.KNOWLEDGE_CARD, UUID.randomUUID(), "ficha", Optional.empty(), false,
                false, Optional.empty(), "Ana", Instant.EPOCH, "x", 1.0, false, Optional.empty(), Optional.empty());

        List<SearchHit> result = run(List.of(card, hit(plain, "suelto"), hit(plain, "suelto")), 10);

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(h -> assertThat(h.context().versions()).isEmpty());
    }
}
