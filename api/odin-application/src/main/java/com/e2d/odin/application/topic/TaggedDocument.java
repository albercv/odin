package com.e2d.odin.application.topic;

import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.topic.DocumentTagging;
import java.util.List;
import java.util.Optional;

/** Un documento de una lista con el estado de su etiquetado (si lo tiene) y sus temas activos. */
public record TaggedDocument(DocumentEntry entry, Optional<DocumentTagging> tagging, List<TopicLabel> topics) {
}
