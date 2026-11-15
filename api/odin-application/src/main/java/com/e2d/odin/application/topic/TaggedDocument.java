package com.e2d.odin.application.topic;

import com.e2d.odin.application.document.ScanLinks;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.topic.DocumentTagging;
import java.util.List;
import java.util.Optional;

/**
 * Un documento de una lista con el estado de su etiquetado (si lo tiene), sus temas activos y sus copias escaneadas.
 */
public record TaggedDocument(DocumentEntry entry, Optional<DocumentTagging> tagging, List<TopicLabel> topics,
        ScanLinks scanLinks) {

    public TaggedDocument(DocumentEntry entry, Optional<DocumentTagging> tagging, List<TopicLabel> topics) {
        this(entry, tagging, topics, ScanLinks.NONE);
    }
}
