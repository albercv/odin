package com.e2d.odin.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryKnowledgeCardRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.knowledge.CardReview;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.knowledge.ReviewDecision;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListMyKnowledgeCardsTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(orgs);
    private final InMemoryKnowledgeCardRepository cards = new InMemoryKnowledgeCardRepository();
    private final ListMyKnowledgeCards useCase = new ListMyKnowledgeCards(orgs, members, cards);
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);

    private KnowledgeCard save(CompanyId company, UserId author, Optional<ProjectId> project, String title,
            CardStatus status, long minutesAfterBase) {
        KnowledgeCard card = new KnowledgeCard(new CardId(UUID.randomUUID()), company, project, title, "Texto " + title,
                Optional.empty(), author, Fixtures.NOW.plus(Duration.ofMinutes(minutesAfterBase)), status,
                Optional.empty());
        cards.save(card);
        return card;
    }

    private KnowledgeCard saveMine(Actor actor, String title, CardStatus status, long minutes) {
        return save(actor.companies().iterator().next(), actor.userId(), Optional.empty(), title, status, minutes);
    }

    private static CardReview review(ReviewDecision decision, Optional<String> note) {
        return new CardReview(new UserId(UUID.randomUUID()), decision, Fixtures.NOW.plusSeconds(60), note);
    }

    @Test
    void listsOnlyOwnCardsNewestFirst() {
        Actor me = Actors.worker(companyA);
        Actor colleague = Actors.worker(companyA);
        KnowledgeCard oldest = saveMine(me, "Antigua", CardStatus.APPROVED, 1);
        KnowledgeCard newest = saveMine(me, "Reciente", CardStatus.PENDING, 3);
        KnowledgeCard middle = saveMine(me, "Media", CardStatus.REJECTED, 2);
        saveMine(colleague, "Del compañero", CardStatus.PENDING, 4);
        saveMine(colleague, "Del compañero aprobada", CardStatus.APPROVED, 5);

        assertThat(useCase.execute(me, Optional.empty())).containsExactly(newest, middle, oldest);
    }

    @Test
    void filtersByStatus() {
        Actor me = Actors.worker(companyA);
        saveMine(me, "Pendiente", CardStatus.PENDING, 1);
        KnowledgeCard rejected = saveMine(me, "Rechazada", CardStatus.REJECTED, 2);
        saveMine(me, "Aprobada", CardStatus.APPROVED, 3);

        assertThat(useCase.execute(me, Optional.of(CardStatus.REJECTED))).containsExactly(rejected);
        assertThat(useCase.execute(me, Optional.of(CardStatus.PENDING))).extracting(KnowledgeCard::title)
                .containsExactly("Pendiente");
    }

    @Test
    void correctedCardShowsBothTexts() {
        Actor me = Actors.worker(companyA);
        KnowledgeCard pending = saveMine(me, "Corregida", CardStatus.PENDING, 1);
        cards.save(pending.correct("Texto corregido", review(ReviewDecision.CORRECT, Optional.of("Ajustada"))));

        KnowledgeCard listed = useCase.execute(me, Optional.empty()).get(0);

        assertThat(listed.status()).isEqualTo(CardStatus.APPROVED);
        assertThat(listed.body()).isEqualTo("Texto corregido");
        assertThat(listed.originalBody()).contains("Texto Corregida");
        assertThat(listed.review()).get().extracting(CardReview::decision).isEqualTo(ReviewDecision.CORRECT);
    }

    @Test
    void rejectedCardShowsReviewNote() {
        Actor me = Actors.worker(companyA);
        KnowledgeCard pending = saveMine(me, "Rechazada", CardStatus.PENDING, 1);
        cards.save(pending.reject(review(ReviewDecision.REJECT, Optional.of("Falta la fecha de la junta"))));

        KnowledgeCard listed = useCase.execute(me, Optional.empty()).get(0);

        assertThat(listed.status()).isEqualTo(CardStatus.REJECTED);
        assertThat(listed.review().flatMap(CardReview::note)).contains("Falta la fecha de la junta");
    }

    @Test
    void capabilityDisabledIsForbidden() {
        CompanyId noSave = Fixtures.companyWith(orgs, EnumSet.of(Capability.SEARCH_KNOWLEDGE));

        assertThatThrownBy(() -> useCase.execute(Actors.worker(noSave), Optional.empty()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sameUserWithKeyOfOtherCompanySeesNothingOfTheFirst() {
        Actor inA = Actors.worker(companyA);
        saveMine(inA, "De la empresa A", CardStatus.PENDING, 1);
        Actor sameUserInB = new Actor(inA.userId(), inA.displayName(), Role.WORKER, Set.of(companyB));

        assertThat(useCase.execute(inA, Optional.empty())).hasSize(1);
        assertThat(useCase.execute(sameUserInB, Optional.empty())).isEmpty();
    }

    @Test
    void ownCardInRestrictedProjectNoLongerVisibleIsHidden() {
        Actor me = Actors.worker(companyA);
        ProjectId closed = Fixtures.project(orgs, companyA);
        members.add(closed, me.userId());
        save(companyA, me.userId(), Optional.of(closed), "Del cerrado", CardStatus.PENDING, 1);
        saveMine(me, "Sin proyecto", CardStatus.PENDING, 2);
        assertThat(useCase.execute(me, Optional.empty())).hasSize(2);

        members.remove(companyA, closed, me.userId());

        assertThat(useCase.execute(me, Optional.empty())).extracting(KnowledgeCard::title)
                .containsExactly("Sin proyecto");
    }

    @Test
    void adminWhoAlsoWorksListsOwnCardsWithKey() {
        Actor withKey = Actors.worker(companyA);
        saveMine(withKey, "Mía", CardStatus.PENDING, 1);
        Actor withPanelSession = new Actor(withKey.userId(), withKey.displayName(), Role.COMPANY_ADMIN,
                Set.of(companyA));

        assertThat(useCase.execute(withKey, Optional.empty())).extracting(KnowledgeCard::title).containsExactly("Mía");
        assertThatThrownBy(() -> useCase.execute(withPanelSession, Optional.empty()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void returnsAtMostOneHundred() {
        Actor me = Actors.worker(companyA);
        for (int i = 0; i < 101; i++) {
            saveMine(me, "Ficha " + i, CardStatus.PENDING, i);
        }

        assertThat(useCase.execute(me, Optional.empty())).hasSize(100).first().extracting(KnowledgeCard::title)
                .isEqualTo("Ficha 100");
    }
}
