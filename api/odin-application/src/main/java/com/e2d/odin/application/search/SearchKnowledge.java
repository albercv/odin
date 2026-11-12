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
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.search.SearchResult;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.util.List;

public class SearchKnowledge {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final KnowledgeSearch search;
    private final QuestionLog questions;
    private final IdGenerator ids;
    private final Clock clock;

    public SearchKnowledge(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            KnowledgeSearch search, QuestionLog questions, IdGenerator ids, Clock clock) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.search = search;
        this.questions = questions;
        this.ids = ids;
        this.clock = clock;
    }

    public SearchResult execute(Actor actor, SearchRequest request) {
        var access = WorkerAccess.require(actor, organizations, projectMembers, Capability.SEARCH_KNOWLEDGE);
        WorkerAccess.requireProject(access.visibility(), request.projectId());
        List<String> terms = request.queries() == null ? List.of() : request.queries();
        if (terms.stream().allMatch(q -> q == null || q.isBlank())) {
            throw new ValidationException("Indica al menos una consulta con texto");
        }
        int limit = WorkerAccess.cap(request.limit(), DEFAULT_LIMIT, MAX_LIMIT);
        SearchResult result = search.search(new SearchQuery(terms, actor.scope(), access.content(),
                request.projectId(), limit));
        questions.record(new QuestionRecord(ids.newId(), access.companyId(), actor.userId(), String.join(" | ", terms),
                result.hits().size(), clock.instant(), QuestionKind.SEARCH));
        return result;
    }
}
