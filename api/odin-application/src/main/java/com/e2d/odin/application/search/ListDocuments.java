package com.e2d.odin.application.search;

import com.e2d.odin.application.context.DocumentContexts;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.application.port.TaggingRepository;
import com.e2d.odin.application.port.TopicRepository;
import com.e2d.odin.application.shared.WorkerAccess;
import com.e2d.odin.application.topic.TaggedDocument;
import com.e2d.odin.application.topic.TaggedDocuments;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import java.util.List;
import java.util.Optional;

public class ListDocuments {

    private final OrganizationRepository organizations;
    private final ProjectMemberRepository projectMembers;
    private final DocumentRepository documents;
    private final TopicRepository topics;
    private final TaggingRepository tagging;
    private final DocumentContexts contexts;

    public ListDocuments(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, TopicRepository topics, TaggingRepository tagging) {
        this(organizations, projectMembers, documents, topics, tagging, DocumentContexts.none());
    }

    public ListDocuments(OrganizationRepository organizations, ProjectMemberRepository projectMembers,
            DocumentRepository documents, TopicRepository topics, TaggingRepository tagging,
            DocumentContexts contexts) {
        this.organizations = organizations;
        this.projectMembers = projectMembers;
        this.documents = documents;
        this.topics = topics;
        this.tagging = tagging;
        this.contexts = contexts;
    }

    public List<TaggedDocument> execute(Actor actor, Optional<ProjectId> projectId, Optional<TopicId> topicId,
            int offset, Optional<Integer> limit) {
        return execute(actor, projectId, topicId, Optional.empty(), offset, limit);
    }

    /** El filtro por marca de caso depende de SEARCH_KNOWLEDGE, como el listado, y no de SIMILAR_CASES. */
    public List<TaggedDocument> execute(Actor actor, Optional<ProjectId> projectId, Optional<TopicId> topicId,
            Optional<Boolean> caseDocument, int offset, Optional<Integer> limit) {
        var access = WorkerAccess.require(actor, organizations, projectMembers, Capability.SEARCH_KNOWLEDGE);
        WorkerAccess.requireProject(access.visibility(), projectId);
        List<Topic> companyTopics = topics.list(access.companyId());
        topicId.filter(id -> companyTopics.stream().noneMatch(t -> t.id().equals(id))).ifPresent(id -> {
            throw new NotFoundException("Tema no encontrado");
        });
        var entries = documents.list(actor.scope(), access.content(), projectId, topicId, caseDocument,
                Optional.of(DocumentStatus.READY), Math.max(0, offset), WorkerAccess.cap(limit, 100, 500));
        List<TaggedDocument> tagged = TaggedDocuments.of(entries, actor.scope(), access.content(), companyTopics,
                tagging, documents, Optional.of(DocumentStatus.READY));
        var corrections = contexts.correctionsOf(actor.scope(), access.content(),
                tagged.stream().map(t -> t.entry().document().id()).toList());
        return tagged.stream().map(t -> t.withCorrections(
                corrections.getOrDefault(t.entry().document().id(), List.of()))).toList();
    }
}
