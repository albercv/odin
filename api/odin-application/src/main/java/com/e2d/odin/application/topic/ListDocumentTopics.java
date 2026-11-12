package com.e2d.odin.application.topic;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.TaggingRepository;
import com.e2d.odin.application.port.TopicRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.topic.DocumentTopic;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Los temas de un documento para quien administra: también los quitados, que el panel enseña aparte. */
public class ListDocumentTopics {

    private final DocumentRepository documents;
    private final TopicRepository topics;
    private final TaggingRepository tagging;
    private final UserRepository users;

    public ListDocumentTopics(DocumentRepository documents, TopicRepository topics, TaggingRepository tagging,
            UserRepository users) {
        this.documents = documents;
        this.topics = topics;
        this.tagging = tagging;
        this.users = users;
    }

    public DocumentTopicsReport execute(Actor actor, DocumentId documentId) {
        AccessPolicy.requireAdmin(actor);
        Document document = documents.findById(actor.scope(), ProjectVisibility.all(), documentId)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        Map<TopicId, Topic> names = topics.list(document.companyId()).stream()
                .collect(Collectors.toMap(Topic::id, Function.identity()));
        List<DocumentTopic> stored = tagging.topicsOf(actor.scope(), documentId);
        Map<UserId, String> deciders = users.findAllById(stored.stream()
                .flatMap(t -> t.decidedBy().stream()).distinct().toList()).stream()
                .collect(Collectors.toMap(User::id, User::displayName));
        List<DocumentTopicLine> lines = stored.stream().filter(t -> names.containsKey(t.topicId()))
                .map(t -> new DocumentTopicLine(t, names.get(t.topicId()).name(),
                        t.decidedBy().map(deciders::get))).toList();
        return new DocumentTopicsReport(lines, Optional.ofNullable(
                tagging.taggingOf(actor.scope(), ProjectVisibility.all(), List.of(documentId)).get(documentId)));
    }
}
