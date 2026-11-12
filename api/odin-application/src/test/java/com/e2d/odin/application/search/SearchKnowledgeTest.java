package com.e2d.odin.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
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
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchKnowledgeTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(orgs);
    private final ScriptedKnowledgeSearch search = new ScriptedKnowledgeSearch();
    private final InMemoryQuestionLog log = new InMemoryQuestionLog();
    private final SearchKnowledge useCase = new SearchKnowledge(orgs, members, search, log, new SequenceIdGenerator(),
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
    void allEmptyQueriesAreRejected() {
        List<List<String>> empties = List.of(List.of(), List.of(" ", ""));
        for (List<String> queries : empties) {
            SearchRequest empty = new SearchRequest(queries, Optional.empty(), Optional.empty());
            assertThatThrownBy(() -> useCase.execute(Actors.worker(companyA), empty))
                    .isInstanceOf(ValidationException.class);
        }
        assertThatThrownBy(() -> useCase.execute(Actors.worker(companyA),
                new SearchRequest(null, Optional.empty(), Optional.empty()))).isInstanceOf(ValidationException.class);
        assertThat(log.records()).isEmpty();
        assertThat(search.lastQuery()).isNull();
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

    @Test
    void unassignedProjectIsInvisible() {
        ProjectId closed = Fixtures.project(orgs, companyA);
        SearchRequest forClosed = new SearchRequest(List.of("x"), Optional.of(closed), Optional.empty());

        assertThatThrownBy(() -> useCase.execute(Actors.worker(companyA), forClosed))
                .isInstanceOf(NotFoundException.class).hasMessage("Proyecto no encontrado");
        assertThat(search.lastQuery()).isNull();
        assertThat(log.records()).isEmpty();

        useCase.execute(Actors.worker(companyA), request(Optional.empty()));
        assertThat(search.lastQuery().visibility().projects()).doesNotContain(closed);
    }

    @Test
    void documentWithoutProjectIsVisibleToEveryWorker() {
        ProjectId closed = Fixtures.project(orgs, companyA);

        useCase.execute(Actors.worker(companyA), request(Optional.empty()));

        ProjectVisibility visibility = search.lastQuery().visibility();
        assertThat(visibility.allows(Optional.empty())).isTrue();
        assertThat(visibility.allows(Optional.of(closed))).isFalse();
    }

    @Test
    void closingACompanyWideProjectHidesItAtOnce() {
        ProjectId project = Fixtures.companyWideProject(orgs, companyA);
        Actor worker = Actors.worker(companyA);
        SearchRequest forProject = new SearchRequest(List.of("x"), Optional.of(project), Optional.empty());
        useCase.execute(worker, forProject);
        assertThat(search.lastQuery().visibility().includes(project)).isTrue();

        orgs.saveProject(orgs.findProject(companyA, project).orElseThrow().withRestricted(true));

        assertThatThrownBy(() -> useCase.execute(worker, forProject)).isInstanceOf(NotFoundException.class);
        useCase.execute(worker, request(Optional.empty()));
        assertThat(search.lastQuery().visibility().includes(project)).isFalse();
    }

    @Test
    void assignedWorkerSeesTheClosedProject() {
        ProjectId closed = Fixtures.project(orgs, companyA);
        Actor worker = Actors.worker(companyA);
        members.add(closed, worker.userId());

        useCase.execute(worker, new SearchRequest(List.of("x"), Optional.of(closed), Optional.empty()));

        assertThat(search.lastQuery().visibility().includes(closed)).isTrue();
        assertThat(search.lastQuery().projectId()).contains(closed);
    }

    @Test
    void deletedProjectContentStaysVisibleToWhoCouldSeeIt() {
        ProjectId closed = Fixtures.project(orgs, companyA);
        ProjectId companyWide = Fixtures.companyWideProject(orgs, companyA);
        Actor member = Actors.worker(companyA);
        Actor stranger = Actors.worker(companyA);
        members.add(closed, member.userId());
        orgs.deleteProject(companyA, closed, Fixtures.NOW);
        orgs.deleteProject(companyA, companyWide, Fixtures.NOW);

        useCase.execute(member, request(Optional.empty()));
        assertThat(search.lastQuery().visibility().includes(closed)).isTrue();
        assertThat(search.lastQuery().visibility().includes(companyWide)).isTrue();
        useCase.execute(stranger, request(Optional.empty()));
        assertThat(search.lastQuery().visibility().includes(closed)).isFalse();
        assertThat(search.lastQuery().visibility().includes(companyWide)).isTrue();
        SearchRequest filtered = new SearchRequest(List.of("x"), Optional.of(closed), Optional.empty());
        assertThatThrownBy(() -> useCase.execute(member, filtered)).isInstanceOf(NotFoundException.class);
    }
}
