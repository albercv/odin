package com.e2d.odin.application.knowledge;

import com.e2d.odin.application.port.KnowledgeCardRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.List;

public class ListCardsForReview {

    private final KnowledgeCardRepository cards;

    public ListCardsForReview(KnowledgeCardRepository cards) {
        this.cards = cards;
    }

    public List<KnowledgeCard> execute(Actor actor, CompanyId companyId, CardStatus status) {
        AccessPolicy.requireAdminOf(actor, companyId);
        return cards.listByStatus(CompanyScope.of(companyId), ProjectVisibility.all(), status);
    }
}
