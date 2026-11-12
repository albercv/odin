package com.e2d.odin.domain.topic;

import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;

/** Un tema de un documento: lo puso la IA o el administrador, y puede estar quitado. */
public record DocumentTopic(DocumentId documentId, TopicId topicId, TopicSource source, TopicState state,
        Optional<String> taggerVersion, Optional<UserId> decidedBy, Optional<Instant> decidedAt) {
}
