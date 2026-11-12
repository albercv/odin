package com.e2d.odin.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.InMemoryQuestionLog;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.knowledge.QuestionKind;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListMyQuestionsTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(orgs);
    private final InMemoryQuestionLog log = new InMemoryQuestionLog();
    private final ListMyQuestions useCase = new ListMyQuestions(orgs, members, log);
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);

    private QuestionRecord ask(CompanyId company, UserId author, String text, long minutes) {
        QuestionRecord record = new QuestionRecord(UUID.randomUUID(), company, author, text, 1,
                Fixtures.NOW.plus(Duration.ofMinutes(minutes)), QuestionKind.SEARCH);
        log.record(record);
        return record;
    }

    private List<String> texts(Actor actor, Optional<Integer> limit) {
        return useCase.execute(actor, limit).stream().map(QuestionRecord::text).toList();
    }

    @Test
    void listsOnlyOwnQuestionsOfKeyCompanyNewestFirst() {
        Actor me = Actors.worker(companyA);
        Actor colleague = Actors.worker(companyA);
        ask(companyA, me.userId(), "primera", 1);
        ask(companyA, me.userId(), "tercera", 3);
        ask(companyA, me.userId(), "segunda", 2);
        ask(companyA, colleague.userId(), "del compañero", 4);
        ask(companyB, me.userId(), "con la clave de B", 5);

        assertThat(texts(me, Optional.empty())).containsExactly("tercera", "segunda", "primera");
    }

    @Test
    void limitDefaultsToFiftyAndCapsAtTwoHundred() {
        Actor me = Actors.worker(companyA);
        for (int i = 0; i < 250; i++) {
            ask(companyA, me.userId(), "pregunta " + i, i);
        }

        assertThat(texts(me, Optional.empty())).hasSize(50).first().isEqualTo("pregunta 249");
        assertThat(texts(me, Optional.of(500))).hasSize(200);
        assertThat(texts(me, Optional.of(3))).hasSize(3);
    }

    @Test
    void actorOfAnotherCompanySeesNothing() {
        Actor me = Actors.worker(companyA);
        ask(companyA, me.userId(), "mía", 1);
        Actor sameUserInB = new Actor(me.userId(), me.displayName(), Role.WORKER, Set.of(companyB));

        assertThat(texts(sameUserInB, Optional.empty())).isEmpty();
        assertThat(texts(Actors.worker(companyB), Optional.empty())).isEmpty();
    }

    @Test
    void requiresSearchCapabilityAndWorkerRole() {
        CompanyId noSearch = Fixtures.companyWith(orgs, EnumSet.of(Capability.SAVE_KNOWLEDGE_CARD));
        Actor worker = Actors.worker(companyA);
        Actor adminWhoAlsoWorks = new Actor(worker.userId(), "x", Role.COMPANY_ADMIN, Set.of(companyA));

        assertThatThrownBy(() -> useCase.execute(Actors.worker(noSearch), Optional.empty()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> useCase.execute(adminWhoAlsoWorks, Optional.empty()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> useCase.execute(Actors.groupAdmin(), Optional.empty()))
                .isInstanceOf(ForbiddenException.class);
    }
}
