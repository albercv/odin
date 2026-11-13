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
        return execute(actor, companyId, offset, limit, false);
    }

    /** Con {@code withdrawn}, la lista incluye también los retirados; solo la ve el administrador. */
    public List<TaggedDocument> execute(Actor actor, CompanyId companyId, int offset, int limit, boolean withdrawn) {
        AccessPolicy.requireAdminOf(actor, companyId);
        int capped = WorkerAccess.cap(Optional.of(limit), 100, 500);
        CompanyScope scope = CompanyScope.of(companyId);
        ProjectVisibility everything = ProjectVisibility.all();
        int from = Math.max(0, offset);
        var entries = withdrawn ? documents.listIncludingWithdrawn(scope, everything, from, capped)
                : documents.list(scope, everything, Optional.empty(), Optional.empty(), Optional.empty(), from,
                        capped);
        return TaggedDocuments.of(entries, scope, everything, topics.list(companyId), tagging);
    }
}
