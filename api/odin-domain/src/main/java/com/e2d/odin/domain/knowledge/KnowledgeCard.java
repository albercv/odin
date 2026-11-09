package com.e2d.odin.domain.knowledge;

import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;

public record KnowledgeCard(
        CardId id,
        CompanyId companyId,
        Optional<ProjectId> projectId,
        String title,
        String body,
        Optional<String> originalBody,
        UserId authorId,
        Instant createdAt,
        CardStatus status,
        Optional<CardReview> review) {

    public KnowledgeCard approve(CardReview cardReview) {
        return copy(body, originalBody, CardStatus.APPROVED, cardReview);
    }

    public KnowledgeCard correct(String correctedBody, CardReview cardReview) {
        return copy(correctedBody, Optional.of(body), CardStatus.APPROVED, cardReview);
    }

    public KnowledgeCard reject(CardReview cardReview) {
        return copy(body, originalBody, CardStatus.REJECTED, cardReview);
    }

    private KnowledgeCard copy(String newBody, Optional<String> original, CardStatus newStatus, CardReview cardReview) {
        return new KnowledgeCard(id, companyId, projectId, title, newBody, original, authorId, createdAt,
                newStatus, Optional.of(cardReview));
    }
}
