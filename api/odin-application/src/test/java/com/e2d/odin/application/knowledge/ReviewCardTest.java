package com.e2d.odin.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryKnowledgeCardRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.knowledge.ReviewDecision;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReviewCardTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryKnowledgeCardRepository cards = new InMemoryKnowledgeCardRepository();
    private final ReviewCard review = new ReviewCard(cards, new MutableClock());
    private final ListCardsForReview list = new ListCardsForReview(cards);
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);
    private final Actor adminA = Actors.companyAdmin(companyA);

    @Test
    void approve() {
        KnowledgeCard card = Fixtures.card(cards, companyA, CardStatus.PENDING);

        KnowledgeCard result = review.execute(adminA, card.id(), ReviewDecision.APPROVE, Optional.empty(),
                Optional.of("ok"));

        assertThat(result.status()).isEqualTo(CardStatus.APPROVED);
        assertThat(result.body()).isEqualTo("Cuerpo original");
        assertThat(result.review().orElseThrow().reviewerId()).isEqualTo(adminA.userId());
        assertThat(result.review().orElseThrow().note()).contains("ok");
        assertThat(cards.get(card.id())).contains(result);
    }

    @Test
    void correctKeepsOriginal() {
        KnowledgeCard card = Fixtures.card(cards, companyA, CardStatus.PENDING);

        KnowledgeCard result = review.execute(adminA, card.id(), ReviewDecision.CORRECT, Optional.of("Corregido"),
                Optional.empty());

        assertThat(result.status()).isEqualTo(CardStatus.APPROVED);
        assertThat(result.body()).isEqualTo("Corregido");
        assertThat(result.originalBody()).contains("Cuerpo original");
    }

    @Test
    void correctRequiresBody() {
        KnowledgeCard card = Fixtures.card(cards, companyA, CardStatus.PENDING);

        assertThatThrownBy(() -> review.execute(adminA, card.id(), ReviewDecision.CORRECT, Optional.empty(),
                Optional.empty())).isInstanceOf(ValidationException.class);
    }

    @Test
    void reject() {
        KnowledgeCard card = Fixtures.card(cards, companyA, CardStatus.PENDING);

        KnowledgeCard result = review.execute(adminA, card.id(), ReviewDecision.REJECT, Optional.empty(),
                Optional.of("Duplicada"));

        assertThat(result.status()).isEqualTo(CardStatus.REJECTED);
        assertThat(result.review().flatMap(r -> r.note())).contains("Duplicada");
    }

    @Test
    void rejectRequiresNote() {
        KnowledgeCard card = Fixtures.card(cards, companyA, CardStatus.PENDING);

        for (Optional<String> note : List.of(Optional.<String>empty(), Optional.of("  "))) {
            assertThatThrownBy(() -> review.execute(adminA, card.id(), ReviewDecision.REJECT, Optional.empty(), note))
                    .isInstanceOf(ValidationException.class);
        }
        assertThat(cards.get(card.id()).orElseThrow().status()).isEqualTo(CardStatus.PENDING);
    }

    @Test
    void cannotReviewTwice() {
        KnowledgeCard card = Fixtures.card(cards, companyA, CardStatus.APPROVED);

        assertThatThrownBy(() -> review.execute(adminA, card.id(), ReviewDecision.REJECT, Optional.empty(),
                Optional.empty())).isInstanceOf(ConflictException.class);
    }

    @Test
    void companyAdminOfOtherCompanyGetsNotFound() {
        KnowledgeCard card = Fixtures.card(cards, companyA, CardStatus.PENDING);

        assertThatThrownBy(() -> review.execute(Actors.companyAdmin(companyB), card.id(), ReviewDecision.APPROVE,
                Optional.empty(), Optional.empty())).isInstanceOf(NotFoundException.class);
        assertThat(cards.get(card.id()).orElseThrow().status()).isEqualTo(CardStatus.PENDING);
    }

    @Test
    void workerCannotReview() {
        KnowledgeCard card = Fixtures.card(cards, companyA, CardStatus.PENDING);

        assertThatThrownBy(() -> review.execute(Actors.worker(companyA), card.id(), ReviewDecision.APPROVE,
                Optional.empty(), Optional.empty())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listsPendingOfOwnCompany() {
        KnowledgeCard mine = Fixtures.card(cards, companyA, CardStatus.PENDING);
        Fixtures.card(cards, companyA, CardStatus.APPROVED);
        Fixtures.card(cards, companyB, CardStatus.PENDING);

        assertThat(list.execute(adminA, companyA, CardStatus.PENDING)).isEqualTo(List.of(mine));
    }

    @Test
    void otherCompanyAdminCannotListCards() {
        Fixtures.card(cards, companyA, CardStatus.PENDING);

        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(companyB), companyA, CardStatus.PENDING))
                .isInstanceOf(ForbiddenException.class);
    }
}
