package com.e2d.odin.application.knowledge;

import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.KnowledgeCardRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Clock;
import java.util.Optional;

public class SaveKnowledgeCard {

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final KnowledgeCardRepository cards;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog audit;

    public SaveKnowledgeCard(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            KnowledgeCardRepository cards, IdGenerator ids, Clock clock, AuditLog audit) {
        this.audit = audit;
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.cards = cards;
        this.ids = ids;
        this.clock = clock;
    }

    public KnowledgeCard execute(Actor actor, String title, String body, Optional<ProjectId> projectId) {
        var access = WorkerAccess.require(actor, organizations, projectMembers, Capability.SAVE_KNOWLEDGE_CARD);
        WorkerAccess.requireProject(access.visibility(), projectId);
        KnowledgeCard card = new KnowledgeCard(new CardId(ids.newId()), access.companyId(), projectId,
                Validation.requireText(title, "el título"), Validation.requireText(body, "el texto"),
                Optional.empty(), actor.userId(), clock.instant(), CardStatus.PENDING, Optional.empty());
        cards.save(card);
        AuditEntry entry = AuditEntry.by(actor, AuditAction.CARD_SAVED).onCompany(card.companyId())
                .on("CARD", card.id().value().toString(), null);
        audit.record(projectId.map(entry::onProject).orElse(entry));
        return card;
    }
}
