package com.e2d.odin.application.knowledge;

import com.e2d.odin.application.port.KnowledgeCardRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.List;
import java.util.Optional;

public class ListCardsForReview {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final KnowledgeCardRepository cards;

    public ListCardsForReview(KnowledgeCardRepository cards) {
        this.cards = cards;
    }

    public record CardPage(List<KnowledgeCard> cards, int total) {
    }

    /** Las más antiguas primero; el límite va de 10 a 50. Un {@code query} en blanco es no buscar. */
    public CardPage page(Actor actor, CompanyId companyId, CardStatus status, Optional<String> query, int offset,
            int limit) {
        AccessPolicy.requireAdminOf(actor, companyId);
        CompanyScope scope = CompanyScope.of(companyId);
        ProjectVisibility everything = ProjectVisibility.all();
        Optional<String> text = query.map(String::trim).filter(q -> !q.isEmpty());
        int capped = Math.max(DEFAULT_LIMIT, WorkerAccess.cap(Optional.of(limit), DEFAULT_LIMIT, MAX_LIMIT));
        return new CardPage(cards.search(scope, everything, status, text, Math.max(0, offset), capped),
                cards.count(scope, everything, status, text));
    }
}
