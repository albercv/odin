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
import com.e2d.odin.domain.knowledge.QuestionKind;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.search.SimilarCase;
import com.e2d.odin.domain.search.SimilarCasesResult;
import com.e2d.odin.domain.search.SourceType;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FindSimilarCasesTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(orgs);
    private final ScriptedKnowledgeSearch search = new ScriptedKnowledgeSearch();
    private final InMemoryQuestionLog log = new InMemoryQuestionLog();
    private final FindSimilarCases useCase = new FindSimilarCases(orgs, members, search, log,
            new SequenceIdGenerator(), new MutableClock());
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);

    private static SimilarCasesRequest request(Optional<ProjectId> project, Optional<Integer> limit) {
        return new SimilarCasesRequest(List.of("filtración", "azotea"), project, limit);
    }

    private static SimilarCase aCase() {
        return new SimilarCase(SourceType.ORIGINAL_DOCUMENT, UUID.randomUUID(), "Caso", Optional.empty(),
                Fixtures.NOW, 1, List.of(), Optional.empty(), false, "...", false, Optional.empty());
    }

    @Test
    void passesTermsScopeVisibilityAndLimit() {
        ProjectId closed = Fixtures.project(orgs, companyA);
        ProjectId member = Fixtures.project(orgs, companyA);
        Actor worker = Actors.worker(companyA);
        members.add(member, worker.userId());

        useCase.execute(worker, request(Optional.of(member), Optional.of(3)));

        var query = search.lastSimilarCasesQuery();
        assertThat(query.terms()).containsExactly("filtración", "azotea");
        assertThat(query.scope()).isEqualTo(CompanyScope.of(companyA));
        assertThat(query.visibility().includes(member)).isTrue();
        assertThat(query.visibility().includes(closed)).isFalse();
        assertThat(query.visibility().allows(Optional.empty())).isTrue();
        assertThat(query.projectId()).contains(member);
        assertThat(query.limit()).isEqualTo(3);
    }

    @Test
    void limitDefaultsToFiveAndCapsAtTen() {
        Actor worker = Actors.worker(companyA);

        useCase.execute(worker, request(Optional.empty(), Optional.empty()));
        assertThat(search.lastSimilarCasesQuery().limit()).isEqualTo(5);
        useCase.execute(worker, request(Optional.empty(), Optional.of(500)));
        assertThat(search.lastSimilarCasesQuery().limit()).isEqualTo(10);
    }

    @Test
    void blankTermsAreRejected() {
        Actor worker = Actors.worker(companyA);
        for (List<String> queries : List.of(List.<String>of(), List.of(" ", ""))) {
            assertThatThrownBy(() -> useCase.execute(worker,
                    new SimilarCasesRequest(queries, Optional.empty(), Optional.empty())))
                    .isInstanceOf(ValidationException.class).hasMessage("Indica al menos una consulta con texto");
        }
        assertThatThrownBy(() -> useCase.execute(worker,
                new SimilarCasesRequest(null, Optional.empty(), Optional.empty())))
                .isInstanceOf(ValidationException.class);
        assertThat(search.lastSimilarCasesQuery()).isNull();
        assertThat(log.records()).isEmpty();
    }

    @Test
    void capabilityDisabledIsForbidden() {
        CompanyId noCases = Fixtures.companyWith(orgs, EnumSet.of(Capability.SEARCH_KNOWLEDGE));

        assertThatThrownBy(() -> useCase.execute(Actors.worker(noCases), request(Optional.empty(), Optional.empty())))
                .isInstanceOf(ForbiddenException.class);
        assertThat(search.lastSimilarCasesQuery()).isNull();
        assertThat(log.records()).isEmpty();
    }

    @Test
    void adminActorIsForbidden() {
        assertThatThrownBy(() -> useCase.execute(Actors.companyAdmin(companyA),
                request(Optional.empty(), Optional.empty()))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> useCase.execute(Actors.groupAdmin(), request(Optional.empty(), Optional.empty())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void restrictedProjectFilterIsNotFound() {
        ProjectId closed = Fixtures.project(orgs, companyA);

        assertThatThrownBy(() -> useCase.execute(Actors.worker(companyA),
                request(Optional.of(closed), Optional.empty()))).isInstanceOf(NotFoundException.class)
                .hasMessage("Proyecto no encontrado");
        assertThat(search.lastSimilarCasesQuery()).isNull();
        assertThat(log.records()).isEmpty();
    }

    @Test
    void openProjectOfWhichNotMemberContributesCases() {
        ProjectId open = Fixtures.companyWideProject(orgs, companyA);

        useCase.execute(Actors.worker(companyA), request(Optional.empty(), Optional.empty()));
        assertThat(search.lastSimilarCasesQuery().visibility().includes(open)).isTrue();
        useCase.execute(Actors.worker(companyA), request(Optional.of(open), Optional.empty()));
        assertThat(search.lastSimilarCasesQuery().projectId()).contains(open);
    }

    @Test
    void recordsQuestionOfKindSimilarCases() {
        Actor worker = Actors.worker(companyA);
        search.returnsCases(new SimilarCasesResult(List.of(aCase(), aCase())));

        var result = useCase.execute(worker, request(Optional.empty(), Optional.empty()));

        assertThat(result.cases()).hasSize(2);
        assertThat(log.records()).hasSize(1);
        QuestionRecord record = log.records().get(0);
        assertThat(record.kind()).isEqualTo(QuestionKind.SIMILAR_CASES);
        assertThat(record.hitCount()).isEqualTo(2);
        assertThat(record.text()).isEqualTo("filtración | azotea");
        assertThat(record.authorId()).isEqualTo(worker.userId());
        assertThat(record.companyId()).isEqualTo(companyA);
    }

    @Test
    void actorOfAnotherCompanySeesOnlyItsCompany() {
        ProjectId projectOfA = Fixtures.companyWideProject(orgs, companyA);

        useCase.execute(Actors.worker(companyB), request(Optional.empty(), Optional.empty()));

        assertThat(search.lastSimilarCasesQuery().scope()).isEqualTo(CompanyScope.of(companyB));
        assertThat(search.lastSimilarCasesQuery().visibility().includes(projectOfA)).isFalse();
        assertThatThrownBy(() -> useCase.execute(Actors.worker(companyB),
                request(Optional.of(projectOfA), Optional.empty()))).isInstanceOf(NotFoundException.class);
    }
}
