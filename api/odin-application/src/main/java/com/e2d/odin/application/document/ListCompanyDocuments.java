package com.e2d.odin.application.document;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.TaggingRepository;
import com.e2d.odin.application.port.TopicRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.application.topic.TaggedDocument;
import com.e2d.odin.application.topic.TaggedDocuments;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.List;
import java.util.Optional;

public class ListCompanyDocuments {

    private final DocumentRepository documents;
    private final TopicRepository topics;
    private final TaggingRepository tagging;

    public ListCompanyDocuments(DocumentRepository documents, TopicRepository topics, TaggingRepository tagging) {
        this.documents = documents;
        this.topics = topics;
        this.tagging = tagging;
    }

    public List<TaggedDocument> execute(Actor actor, CompanyId companyId, int offset, int limit) {
        AccessPolicy.requireAdminOf(actor, companyId);
        int capped = WorkerAccess.cap(Optional.of(limit), 100, 500);
        CompanyScope scope = CompanyScope.of(companyId);
        ProjectVisibility everything = ProjectVisibility.all();
        var entries = documents.list(scope, everything, Optional.empty(), Optional.empty(), Optional.empty(),
                Math.max(0, offset), capped);
        return TaggedDocuments.of(entries, scope, everything, topics.list(companyId), tagging);
    }
}
