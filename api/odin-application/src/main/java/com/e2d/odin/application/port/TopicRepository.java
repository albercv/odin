package com.e2d.odin.application.port;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TopicRepository {

    /** Los temas vivos de la empresa, por nombre. */
    List<Topic> list(CompanyId companyId);

    Optional<Topic> find(CompanyId companyId, TopicId topicId);

    /** Sube la versión de la lista si el tema es nuevo o cambia su nombre o su definición. */
    void save(Topic topic);

    /** Borrado lógico: no sube la versión. */
    void delete(CompanyId companyId, TopicId topicId, Instant at);

    int listVersion(CompanyId companyId);
}
