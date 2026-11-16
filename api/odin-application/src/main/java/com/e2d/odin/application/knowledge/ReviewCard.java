package com.e2d.odin.application.knowledge;

import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.KnowledgeCardRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.knowledge.CardReview;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.knowledge.ReviewDecision;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.time.Clock;
import java.util.Optional;

public class ReviewCard {

    private final KnowledgeCardRepository cards;
    private final Clock clock;
    private final AuditLog audit;

    public ReviewCard(KnowledgeCardRepository cards, Clock clock, AuditLog audit) {
        this.cards = cards;
        this.clock = clock;
        this.audit = audit;
    }

    public KnowledgeCard execute(Actor actor, CardId id, ReviewDecision decision, Optional<String> correctedBody,
            Optional<String> note) {
        AccessPolicy.requireAdmin(actor);
        KnowledgeCard card = cards.findForReview(actor.scope(), ProjectVisibility.all(), id)
                .orElseThrow(() -> new NotFoundException("Ficha no encontrada"));
        if (card.status() != CardStatus.PENDING) {
            throw new ConflictException("La ficha ya está revisada");
        }
        if (decision == ReviewDecision.REJECT) {
            Validation.requireText(note.orElse(null), "el motivo del rechazo");
        }
        CardReview review = new CardReview(actor.userId(), decision, clock.instant(), note);
        KnowledgeCard reviewed = switch (decision) {
            case APPROVE -> card.approve(review);
            case REJECT -> card.reject(review);
            case CORRECT -> card.correct(Validation.requireText(correctedBody.orElse(null), "el texto corregido"),
                    review);
        };
        cards.save(reviewed);
        AuditEntry entry = AuditEntry.by(actor, switch (decision) {
            case APPROVE -> AuditAction.CARD_APPROVED;
            case REJECT -> AuditAction.CARD_REJECTED;
            case CORRECT -> AuditAction.CARD_CORRECTED;
        }).onCompany(card.companyId()).on("CARD", card.id().value().toString(), null);
        audit.record(card.projectId().map(entry::onProject).orElse(entry));
        return reviewed;
    }
}
