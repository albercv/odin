package com.e2d.odin.application.port;

import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyScope;
import java.util.List;
import java.util.Optional;

public interface KnowledgeCardRepository {

    void save(KnowledgeCard card);

    Optional<KnowledgeCard> findById(CompanyScope scope, CardId id);

    List<KnowledgeCard> listByStatus(CompanyScope scope, CardStatus status);
}
