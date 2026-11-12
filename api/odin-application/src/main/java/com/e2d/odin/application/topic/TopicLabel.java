package com.e2d.odin.application.topic;

import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicSource;

/** Un tema activo de un documento tal como lo enseñan las listas: con su nombre y quién lo puso. */
public record TopicLabel(TopicId id, String name, TopicSource source) {
}
