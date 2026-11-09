package com.e2d.odin.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryKnowledgeCardRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SaveKnowledgeCardTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryKnowledgeCardRepository cards = new InMemoryKnowledgeCardRepository();
    private final SaveKnowledgeCard save = new SaveKnowledgeCard(orgs, cards, new SequenceIdGenerator(),
            new MutableClock());
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);

    @Test
    void createsPendingCardWithAuthor() {
        Actor worker = Actors.worker(companyA);

        KnowledgeCard card = save.execute(worker, " Titulo ", "Cuerpo", Optional.empty());

        assertThat(card.status()).isEqualTo(CardStatus.PENDING);
        assertThat(card.authorId()).isEqualTo(worker.userId());
        assertThat(card.companyId()).isEqualTo(companyA);
        assertThat(card.title()).isEqualTo("Titulo");
        assertThat(cards.get(card.id())).contains(card);
    }

    @Test
    void requiresSaveCapability() {
        CompanyId noSave = Fixtures.companyWith(orgs, EnumSet.of(Capability.SEARCH_KNOWLEDGE));

        assertThatThrownBy(() -> save.execute(Actors.worker(noSave), "t", "b", Optional.empty()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectsBlankBody() {
        assertThatThrownBy(() -> save.execute(Actors.worker(companyA), "t", " ", Optional.empty()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void workerOfOtherCompanyCannotUseProjectOfCompanyA() {
        var projectOfA = Fixtures.project(orgs, companyA);

        assertThatThrownBy(() -> save.execute(Actors.worker(companyB), "t", "b", Optional.of(projectOfA)))
                .isInstanceOf(NotFoundException.class);
        assertThat(cards.listByStatus(Actors.groupAdmin().scope(), CardStatus.PENDING)).isEmpty();
    }

    @Test
    void adminCannotSave() {
        assertThatThrownBy(() -> save.execute(Actors.companyAdmin(companyA), "t", "b", Optional.empty()))
                .isInstanceOf(ForbiddenException.class);
    }
}
