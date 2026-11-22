package com.e2d.odin.application.context;

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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * El paso común de todas las salidas que dan contenido de documentos a la IA: la cadena de versiones, las correcciones y
 * si falta algo por ver. Recibe ids de documentos ya visibles para quien lee.
 */
public class DocumentContexts {

    private final DocumentContextRepository repository;

    public DocumentContexts(DocumentContextRepository repository) {
        this.repository = repository;
    }

    /** Sin repositorio: ningún documento tiene contexto. Para quien no lo usa. */
    public static DocumentContexts none() {
        return new DocumentContexts(new NoContexts());
    }

    public Map<DocumentId, DocumentContext> of(CompanyScope scope, ProjectVisibility visibility,
            Collection<DocumentId> ids) {
        return repository.contexts(scope, visibility, ids);
    }

    public Map<DocumentId, List<DocumentCorrection>> correctionsOf(CompanyScope scope, ProjectVisibility visibility,
            Collection<DocumentId> ids) {
        return repository.corrections(scope, visibility, ids);
    }

    /**
     * Agrupa las versiones de una misma cadena en un solo resultado (el de la versión más reciente entre los
     * resultados), se queda con {@code limit} y añade a cada uno su contexto con el mejor fragmento de cada versión.
     * No cambia el orden de los candidatos.
     */
    public List<SearchHit> forSearch(CompanyScope scope, ProjectVisibility visibility, List<String> terms,
            List<SearchHit> candidates, int limit) {
        List<DocumentId> ids = candidates.stream().filter(DocumentContexts::isDocument)
                .map(h -> new DocumentId(h.sourceId())).distinct().toList();
        Map<DocumentId, DocumentContext> contexts = repository.contexts(scope, visibility, ids);
        List<SearchHit> kept = group(candidates, contexts).stream().limit(limit).toList();
        Set<DocumentId> chain = new HashSet<>();
        kept.forEach(h -> contextOf(h, contexts).versions().forEach(v -> chain.add(v.documentId())));
        Map<DocumentId, PageText> matches = repository.bestMatches(scope, visibility, terms, chain);
        return kept.stream().map(h -> isDocument(h)
                ? h.withContext(contextOf(h, contexts).withMatches(matches)) : h).toList();
    }

    private static boolean isDocument(SearchHit hit) {
        return hit.sourceType() == SourceType.ORIGINAL_DOCUMENT;
    }

    private static DocumentContext contextOf(SearchHit hit, Map<DocumentId, DocumentContext> contexts) {
        return isDocument(hit) ? contexts.getOrDefault(new DocumentId(hit.sourceId()), DocumentContext.NONE)
                : DocumentContext.NONE;
    }

    private static List<SearchHit> group(List<SearchHit> candidates, Map<DocumentId, DocumentContext> contexts) {
        Map<DocumentId, SearchHit> newest = new HashMap<>();
        for (SearchHit hit : candidates) {
            chainKey(hit, contexts).ifPresent(key -> newest.merge(key, hit,
                    (a, b) -> position(b, contexts) > position(a, contexts) ? b : a));
        }
        return candidates.stream().filter(h -> chainKey(h, contexts).map(k -> newest.get(k) == h).orElse(true))
                .toList();
    }

    private static java.util.Optional<DocumentId> chainKey(SearchHit hit, Map<DocumentId, DocumentContext> contexts) {
        List<DocumentVersion> versions = contextOf(hit, contexts).versions();
        return versions.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(versions.get(0).documentId());
    }

    private static int position(SearchHit hit, Map<DocumentId, DocumentContext> contexts) {
        List<DocumentVersion> versions = contextOf(hit, contexts).versions();
        for (int i = 0; i < versions.size(); i++) {
            if (versions.get(i).documentId().value().equals(hit.sourceId())) {
                return i;
            }
        }
        return -1;
    }

    private static final class NoContexts implements DocumentContextRepository {
        @Override
        public Map<DocumentId, DocumentContext> contexts(CompanyScope scope, ProjectVisibility visibility,
                Collection<DocumentId> ids) {
            return Map.of();
        }

        @Override
        public Map<DocumentId, List<DocumentCorrection>> corrections(CompanyScope scope, ProjectVisibility visibility,
                Collection<DocumentId> ids) {
            return Map.of();
        }

        @Override
        public Map<DocumentId, PageText> bestMatches(CompanyScope scope, ProjectVisibility visibility,
                List<String> terms, Collection<DocumentId> ids) {
            return Map.of();
        }
    }
}
