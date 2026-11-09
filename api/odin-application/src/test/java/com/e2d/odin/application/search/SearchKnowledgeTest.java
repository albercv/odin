package com.e2d.odin.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryQuestionLog;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.ScriptedKnowledgeSearch;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchResult;
import com.e2d.odin.domain.search.SourceType;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchKnowledgeTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final ScriptedKnowledgeSearch search = new ScriptedKnowledgeSearch();
    private final InMemoryQuestionLog log = new InMemoryQuestionLog();
    private final SearchKnowledge useCase = new SearchKnowledge(orgs, search, log, new SequenceIdGenerator(),
            new MutableClock());
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);

    private SearchRequest request(Optional<Integer> limit) {
        return new SearchRequest(List.of("franquicia", "seguro"), Optional.empty(), limit);
    }

    @Test
    void usesActorScopeOnly() {
        useCase.execute(Actors.worker(companyB), request(Optional.empty()));

        assertThat(search.lastQuery().scope()).isEqualTo(CompanyScope.of(companyB));
        assertThat(search.lastQuery().terms()).containsExactly("franquicia", "seguro");
        assertThat(search.lastQuery().limit()).isEqualTo(10);
    }

    @Test
    void recordsQuestionWithAuthor() {
        Actor worker = Actors.worker(companyA);
        SearchHit hit = new SearchHit(SourceType.KNOWLEDGE_CARD, UUID.randomUUID(), "t", Optional.empty(), false,
                Optional.empty(), "Ana", Fixtures.NOW, "...", 1.0);
        search.returns(new SearchResult(List.of(hit)));

        useCase.execute(worker, request(Optional.empty()));

        assertThat(log.records()).hasSize(1);
        QuestionRecord record = log.records().get(0);
        assertThat(record.authorId()).isEqualTo(worker.userId());
        assertThat(record.companyId()).isEqualTo(companyA);
        assertThat(record.text()).isEqualTo("franquicia | seguro");
        assertThat(record.hitCount()).isEqualTo(1);
    }

    @Test
    void capsLimitAt50() {
        useCase.execute(Actors.worker(companyA), request(Optional.of(500)));

        assertThat(search.lastQuery().limit()).isEqualTo(50);
    }

    @Test
    void requiresSearchCapability() {
        CompanyId noSearch = Fixtures.companyWith(orgs, EnumSet.of(Capability.SAVE_KNOWLEDGE_CARD));

        assertThatThrownBy(() -> useCase.execute(Actors.worker(noSearch), request(Optional.empty())))
                .isInstanceOf(ForbiddenException.class);
        assertThat(log.records()).isEmpty();
    }

    @Test
    void adminCannotSearch() {
        assertThatThrownBy(() -> useCase.execute(Actors.companyAdmin(companyA), request(Optional.empty())))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> useCase.execute(Actors.groupAdmin(), request(Optional.empty())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void workerOfOtherCompanyCannotSearchInProjectOfCompanyA() {
        var projectOfA = Fixtures.project(orgs, companyA);
        SearchRequest forA = new SearchRequest(List.of("x"), Optional.of(projectOfA), Optional.empty());

        assertThatThrownBy(() -> useCase.execute(Actors.worker(companyB), forA)).isInstanceOf(NotFoundException.class);
        assertThat(search.lastQuery()).isNull();
    }
}
