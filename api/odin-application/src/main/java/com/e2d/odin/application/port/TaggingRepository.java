package com.e2d.odin.application.port;

import com.e2d.odin.application.shared.TaggingSettings;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.topic.DocumentTagging;
import com.e2d.odin.domain.topic.DocumentTopic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicState;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface TaggingRepository {

    /**
     * Toma el siguiente documento {@code READY} de una empresa con temas: primero los que nunca se etiquetaron,
     * después los de una versión anterior de la lista, y los que llevan etiquetándose más allá de su plazo. Lo deja
     * en {@code TAGGING}, con un intento más y el plazo que dan los tramos de su texto.
     */
    Optional<TaggingJob> claimNext(Instant now, TaggingSettings settings);

    /** Bloquea la fila del documento y sustituye los temas de la IA, sin tocar los que decidió el administrador. */
    void saveResult(DocumentId documentId, Set<TopicId> aiTopics, String taggerVersion, Instant now);

    void markFailed(DocumentId documentId, String reason);

    void returnToQueue(DocumentId documentId, boolean countAttempt);

    /** Borra el estado del documento: vuelve a la cola. Las decisiones del administrador se conservan. */
    void retag(CompanyScope scope, DocumentId documentId);

    List<DocumentTopic> topicsOf(CompanyScope scope, DocumentId documentId);

    /** Los temas {@code ACTIVE} de cada documento pedido (los de temas borrados no salen), en una sola consulta. */
    Map<DocumentId, List<DocumentTopic>> activeTopicsOf(CompanyScope scope, ProjectVisibility visibility,
            Collection<DocumentId> documentIds);

    /** El estado del etiquetado de cada documento pedido que lo tenga, en una sola consulta. */
    Map<DocumentId, DocumentTagging> taggingOf(CompanyScope scope, ProjectVisibility visibility,
            Collection<DocumentId> documentIds);

    /** Bloquea la fila del documento, como {@link #saveResult}, y guarda la decisión del administrador. */
    void decide(CompanyScope scope, DocumentId documentId, TopicId topicId, TopicState state, UserId by, Instant at);

    /** Documentos visibles {@code READY} que no están {@code DONE} con la versión actual de la lista. */
    int pendingCount(CompanyId companyId, ProjectVisibility visibility);

    /** Lo mismo sin filtro de proyectos, para el panel del administrador (D-043). */
    TaggingBacklog backlog(CompanyId companyId);
}
