package com.e2d.odin.application.topic;

import com.e2d.odin.application.port.TaggingRepository;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.topic.DocumentTagging;
import com.e2d.odin.domain.topic.DocumentTopic;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Añade a las entradas de una lista su estado de etiquetado y sus temas, con dos consultas para toda la página. */
public final class TaggedDocuments {

    private TaggedDocuments() {
    }

    public static List<TaggedDocument> of(List<DocumentEntry> entries, CompanyScope scope, ProjectVisibility visibility,
            List<Topic> companyTopics, TaggingRepository tagging) {
        List<DocumentId> ids = entries.stream().map(e -> e.document().id()).toList();
        Map<TopicId, Topic> byId = companyTopics.stream().collect(Collectors.toMap(Topic::id, Function.identity()));
        Map<DocumentId, List<DocumentTopic>> topics = tagging.activeTopicsOf(scope, visibility, ids);
        Map<DocumentId, DocumentTagging> states = tagging.taggingOf(scope, visibility, ids);
        return entries.stream().map(entry -> {
            DocumentId id = entry.document().id();
            List<TopicLabel> labels = topics.getOrDefault(id, List.of()).stream()
                    .filter(t -> byId.containsKey(t.topicId()))
                    .map(t -> new TopicLabel(t.topicId(), byId.get(t.topicId()).name(), t.source())).toList();
            return new TaggedDocument(entry, Optional.ofNullable(states.get(id)), labels);
        }).toList();
    }
}
