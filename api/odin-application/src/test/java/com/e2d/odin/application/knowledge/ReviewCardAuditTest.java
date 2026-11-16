package com.e2d.odin.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryKnowledgeCardRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.audit.AuditOutcome;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.knowledge.ReviewDecision;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Una entrada por revisión de ficha: su id y su empresa; nunca el título, el texto ni la nota. */
class ReviewCardAuditTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryKnowledgeCardRepository cards = new InMemoryKnowledgeCardRepository();
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final ReviewCard review = new ReviewCard(cards, new MutableClock(), audit);
    private final CompanyId company = Fixtures.company(orgs);
    private final Actor admin = Actors.companyAdmin(company);

    private AuditEntry review(ReviewDecision decision, AuditAction expected, Optional<String> corrected) {
        KnowledgeCard card = Fixtures.card(cards, company, CardStatus.PENDING, "TITULO-SECRETO", "CUERPO-SECRETO");
        audit.entries.clear();

        review.execute(admin, card.id(), decision, corrected, Optional.of("NOTA-SECRETA"));

        assertThat(audit.entries).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo(expected));
        AuditEntry e = audit.entries.get(0);
        assertThat(e.outcome()).isEqualTo(AuditOutcome.DONE);
        assertThat(e.actorUserId()).contains(admin.userId());
        assertThat(e.actorRole()).contains("COMPANY_ADMIN");
        assertThat(e.company()).contains(company);
        assertThat(e.target()).contains(new AuditEntry.Target("CARD", card.id().value().toString(), null));
        return e;
    }

    @Test
    void approveCorrectAndRejectEachRecordOneEntry() {
        review(ReviewDecision.APPROVE, AuditAction.CARD_APPROVED, Optional.empty());
        review(ReviewDecision.CORRECT, AuditAction.CARD_CORRECTED, Optional.of("TEXTO-CORREGIDO"));
        review(ReviewDecision.REJECT, AuditAction.CARD_REJECTED, Optional.empty());
    }

    @Test
    void noCardEntryCarriesTitleTextOrNote() {
        AuditEntry e = review(ReviewDecision.CORRECT, AuditAction.CARD_CORRECTED, Optional.of("TEXTO-CORREGIDO"));

        assertThat(e.toString()).doesNotContain("SECRET", "CORREGIDO");
        assertThat(e.details()).isEmpty();
    }
}
