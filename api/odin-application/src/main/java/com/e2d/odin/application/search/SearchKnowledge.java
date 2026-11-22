package com.e2d.odin.application.search;

import com.e2d.odin.application.context.DocumentContexts;
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
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.search.SearchResult;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.util.List;

public class SearchKnowledge {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    /** Se piden más candidatos de los que se devuelven: agrupar las versiones de una cadena libera huecos. */
    private static final int CANDIDATE_FACTOR = 4;
    private static final int MAX_CANDIDATES = 200;

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final KnowledgeSearch search;
    private final QuestionLog questions;
    private final IdGenerator ids;
    private final Clock clock;
    private final DocumentContexts contexts;

    public SearchKnowledge(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            KnowledgeSearch search, QuestionLog questions, IdGenerator ids, Clock clock) {
        this(organizations, projectMembers, search, questions, ids, clock, DocumentContexts.none());
    }

    public SearchKnowledge(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            KnowledgeSearch search, QuestionLog questions, IdGenerator ids, Clock clock, DocumentContexts contexts) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.search = search;
        this.questions = questions;
        this.ids = ids;
        this.clock = clock;
        this.contexts = contexts;
    }

    public SearchResult execute(Actor actor, SearchRequest request) {
        var access = WorkerAccess.require(actor, organizations, projectMembers, Capability.SEARCH_KNOWLEDGE);
        WorkerAccess.requireProject(access.visibility(), request.projectId());
        List<String> terms = request.queries() == null ? List.of() : request.queries();
        if (terms.stream().allMatch(q -> q == null || q.isBlank())) {
            throw new ValidationException("Indica al menos una consulta con texto");
        }
        int limit = WorkerAccess.cap(request.limit(), DEFAULT_LIMIT, MAX_LIMIT);
        int candidates = Math.max(limit, Math.min(limit * CANDIDATE_FACTOR, MAX_CANDIDATES));
        List<SearchHit> found = search.search(new SearchQuery(terms, actor.scope(), access.content(),
                request.projectId(), candidates, actor.userId(), request.caseId())).hits();
        SearchResult result = new SearchResult(contexts.forSearch(actor.scope(), access.content(), terms, found, limit));
        questions.record(new QuestionRecord(ids.newId(), access.companyId(), actor.userId(), String.join(" | ", terms),
                result.hits().size(), clock.instant(), QuestionKind.SEARCH));
        return result;
    }
}
