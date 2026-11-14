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

    /** Para el administrador que revisa: no filtra por estado ni por autor, así que un trabajador no debe usarlo. */
    Optional<KnowledgeCard> findForReview(CompanyScope scope, ProjectVisibility visibility, CardId id);

    List<KnowledgeCard> listByStatus(CompanyScope scope, ProjectVisibility visibility, CardStatus status);

    /**
     * Las fichas de un estado, de la más antigua a la más nueva. Con {@code query}, solo las que lo contienen en el
     * título o el cuerpo, sin mayúsculas ni acentos y con {@code %} y {@code _} como texto.
     */
    List<KnowledgeCard> search(CompanyScope scope, ProjectVisibility visibility, CardStatus status,
            Optional<String> query, int offset, int limit);

    /** Cuántas fichas devuelve {@link #search} sin paginar. */
    int count(CompanyScope scope, ProjectVisibility visibility, CardStatus status, Optional<String> query);

    /** Las fichas de un autor, de la más reciente a la más antigua, dentro de la empresa y los proyectos visibles. */
    List<KnowledgeCard> listByAuthor(CompanyScope scope, ProjectVisibility visibility, UserId authorId,
            Optional<CardStatus> status, int limit);
}
