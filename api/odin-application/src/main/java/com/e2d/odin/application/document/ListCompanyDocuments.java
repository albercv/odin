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

    public record DocumentPage(List<TaggedDocument> documents, int total) {
    }

    /** Con {@code withdrawn}, la lista trae solo los retirados. Un {@code query} en blanco es no buscar. */
    public DocumentPage page(Actor actor, CompanyId companyId, int offset, int limit, boolean withdrawn,
            Optional<String> query) {
        AccessPolicy.requireAdminOf(actor, companyId);
        int capped = WorkerAccess.cap(Optional.of(limit), 100, 500);
        CompanyScope scope = CompanyScope.of(companyId);
        ProjectVisibility everything = ProjectVisibility.all();
        Optional<String> title = query.map(String::strip).filter(q -> !q.isEmpty());
        var entries = documents.search(scope, everything, withdrawn, title, Math.max(0, offset), capped);
        return new DocumentPage(TaggedDocuments.of(entries, scope, everything, topics.list(companyId), tagging),
                documents.count(scope, everything, withdrawn, title));
    }
}
