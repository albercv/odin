package com.e2d.odin.application.search;

import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.KnowledgeSearch;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.QuestionLog;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.search.SearchResult;
import com.e2d.odin.domain.shared.CompanyId;
import java.time.Clock;
import java.util.List;

public class SearchKnowledge {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final OrganizationRepository organizations;
    private final KnowledgeSearch search;
    private final QuestionLog questions;
    private final IdGenerator ids;
    private final Clock clock;

    public SearchKnowledge(OrganizationRepository organizations, KnowledgeSearch search, QuestionLog questions,
            IdGenerator ids, Clock clock) {
        this.organizations = organizations;
        this.search = search;
        this.questions = questions;
        this.ids = ids;
        this.clock = clock;
    }

    public SearchResult execute(Actor actor, SearchRequest request) {
        CompanyId companyId = WorkerAccess.require(actor, organizations, Capability.SEARCH_KNOWLEDGE);
        WorkerAccess.requireProject(organizations, companyId, request.projectId());
        List<String> terms = request.queries() == null ? List.of() : request.queries();
        int limit = WorkerAccess.cap(request.limit(), DEFAULT_LIMIT, MAX_LIMIT);
        SearchResult result = search.search(new SearchQuery(terms, actor.scope(), request.projectId(), limit));
        String text = terms.isEmpty() ? null : String.join(" | ", terms);
        questions.record(new QuestionRecord(ids.newId(), companyId, actor.userId(), text, result.hits().size(),
                clock.instant()));
        return result;
    }
}
