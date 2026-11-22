package com.e2d.odin.application.topic;

import com.e2d.odin.application.document.ScanLinks;
import com.e2d.odin.domain.context.DocumentCorrection;
import com.e2d.odin.domain.context.CorrectionDirection;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.topic.DocumentTagging;
import java.util.List;
import java.util.Optional;

/**
 * Un documento de una lista con el estado de su etiquetado (si lo tiene), sus temas activos, sus copias escaneadas y
 * cuántas correcciones tiene (solo el recuento, no la lista: pesa).
 */
public record TaggedDocument(DocumentEntry entry, Optional<DocumentTagging> tagging, List<TopicLabel> topics,
        ScanLinks scanLinks, boolean corrected, int correctionCount) {

    public TaggedDocument(DocumentEntry entry, Optional<DocumentTagging> tagging, List<TopicLabel> topics,
            ScanLinks scanLinks) {
        this(entry, tagging, topics, scanLinks, false, 0);
    }

    public TaggedDocument(DocumentEntry entry, Optional<DocumentTagging> tagging, List<TopicLabel> topics) {
        this(entry, tagging, topics, ScanLinks.NONE);
    }

    public TaggedDocument withCorrections(List<DocumentCorrection> corrections) {
        return new TaggedDocument(entry, tagging, topics, scanLinks,
                corrections.stream().anyMatch(c -> c.direction() == CorrectionDirection.CORRECTED_BY),
                corrections.size());
    }
}
