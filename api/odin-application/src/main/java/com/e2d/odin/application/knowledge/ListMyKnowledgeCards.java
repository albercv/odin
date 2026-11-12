package com.e2d.odin.application.knowledge;

import com.e2d.odin.application.port.KnowledgeCardRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.organization.Capability;
import java.util.List;
import java.util.Optional;

/** Las fichas que el actor guardó, en la empresa de su clave y en los proyectos que ve (D-036, D-047). */
public class ListMyKnowledgeCards {

    private static final int LIMIT = 100;

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final KnowledgeCardRepository cards;

    public ListMyKnowledgeCards(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            KnowledgeCardRepository cards) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.cards = cards;
    }

    public List<KnowledgeCard> execute(Actor actor, Optional<CardStatus> status) {
        var access = WorkerAccess.require(actor, organizations, projectMembers, Capability.SAVE_KNOWLEDGE_CARD);
        return cards.listByAuthor(actor.scope(), access.content(), actor.userId(), status, LIMIT);
    }
}
