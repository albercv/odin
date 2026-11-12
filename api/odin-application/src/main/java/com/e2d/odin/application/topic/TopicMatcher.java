package com.e2d.odin.application.topic;

import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicNames;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Coteja la respuesta con la lista: un tema está si su nombre normalizado es parte de la respuesta normalizada. */
public final class TopicMatcher {

    private TopicMatcher() {
    }

    public static Set<TopicId> match(String answer, List<Topic> topics) {
        String normalized = TopicNames.normalize(answer);
        return topics.stream().filter(t -> normalized.contains(TopicNames.normalize(t.name()))).map(Topic::id)
                .collect(Collectors.toSet());
    }
}
