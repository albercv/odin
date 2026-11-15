package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.KnowledgeCardRepository;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryKnowledgeCardRepository implements KnowledgeCardRepository {

    private final Map<CardId, KnowledgeCard> cards = new LinkedHashMap<>();

    public Optional<KnowledgeCard> get(CardId id) {
        return Optional.ofNullable(cards.get(id));
    }

    @Override
    public void save(KnowledgeCard card) {
        cards.put(card.id(), card);
    }

    @Override
    public Optional<KnowledgeCard> findForReview(CompanyScope scope, ProjectVisibility visibility, CardId id) {
        return Optional.ofNullable(cards.get(id))
                .filter(c -> scope.includes(c.companyId()) && visibility.allows(c.projectId()));
    }

    @Override
    public List<KnowledgeCard> listByAuthor(CompanyScope scope, ProjectVisibility visibility, UserId authorId,
            Optional<CardStatus> status, int limit) {
        return cards.values().stream().filter(c -> c.authorId().equals(authorId) && scope.includes(c.companyId())
                && visibility.allows(c.projectId()) && status.map(s -> s == c.status()).orElse(true))
                .sorted(Comparator.comparing(KnowledgeCard::createdAt).reversed()).limit(limit).toList();
    }

    @Override
    public List<KnowledgeCard> search(CompanyScope scope, ProjectVisibility visibility, CardStatus status,
            Optional<String> query, int offset, int limit) {
        return matching(scope, visibility, status, query).skip(offset).limit(limit).toList();
    }

    @Override
    public int count(CompanyScope scope, ProjectVisibility visibility, CardStatus status, Optional<String> query) {
        return (int) matching(scope, visibility, status, query).count();
    }

    private java.util.stream.Stream<KnowledgeCard> matching(CompanyScope scope, ProjectVisibility visibility,
            CardStatus status, Optional<String> query) {
        return cards.values().stream().filter(c -> scope.includes(c.companyId())
                && visibility.allows(c.projectId()) && c.status() == status)
                .filter(c -> query.map(q -> fold(c.title() + " " + c.body()).contains(fold(q))).orElse(true));
    }

    private static String fold(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    @Override
    public List<KnowledgeCard> listByStatus(CompanyScope scope, ProjectVisibility visibility, CardStatus status) {
        return cards.values().stream().filter(c -> scope.includes(c.companyId())
                && visibility.allows(c.projectId()) && c.status() == status).toList();
    }
}
