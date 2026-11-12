package com.e2d.odin.application.search;

import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.KnowledgeSearch;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.port.QuestionLog;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.knowledge.QuestionKind;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.search.SimilarCasesQuery;
import com.e2d.odin.domain.search.SimilarCasesResult;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

public class FindSimilarCases {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final KnowledgeSearch search;
    private final QuestionLog questions;
    private final IdGenerator ids;
    private final Clock clock;

    public FindSimilarCases(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            KnowledgeSearch search, QuestionLog questions, IdGenerator ids, Clock clock) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.search = search;
        this.questions = questions;
        this.ids = ids;
        this.clock = clock;
    }

    public SimilarCasesResult execute(Actor actor, SimilarCasesRequest request) {
        var access = WorkerAccess.require(actor, organizations, projectMembers, Capability.SIMILAR_CASES,
                Capability.SEARCH_KNOWLEDGE);
        WorkerAccess.requireProject(access.visibility(), request.projectId());
        List<String> terms = Optional.ofNullable(request.queries()).orElse(List.of());
        if (terms.stream().allMatch(q -> q == null || q.isBlank())) {
            throw new ValidationException("Indica al menos una consulta con texto");
        }
        int limit = WorkerAccess.cap(request.limit(), DEFAULT_LIMIT, MAX_LIMIT);
        SimilarCasesResult result = search.findSimilarCases(new SimilarCasesQuery(terms, actor.scope(),
                access.content(), request.projectId(), limit));
        questions.record(new QuestionRecord(ids.newId(), access.companyId(), actor.userId(), String.join(" | ", terms),
                result.cases().size(), clock.instant(), QuestionKind.SIMILAR_CASES));
        return result;
    }
}
