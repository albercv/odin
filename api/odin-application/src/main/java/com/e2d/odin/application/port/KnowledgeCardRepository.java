package com.e2d.odin.application.port;

import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Optional;

public interface KnowledgeCardRepository {

    void save(KnowledgeCard card);

    Optional<KnowledgeCard> findById(CompanyScope scope, ProjectVisibility visibility, CardId id);

    List<KnowledgeCard> listByStatus(CompanyScope scope, ProjectVisibility visibility, CardStatus status);

    /** Las fichas de un autor, de la más reciente a la más antigua, dentro de la empresa y los proyectos visibles. */
    List<KnowledgeCard> listByAuthor(CompanyScope scope, ProjectVisibility visibility, UserId authorId,
            Optional<CardStatus> status, int limit);
}
