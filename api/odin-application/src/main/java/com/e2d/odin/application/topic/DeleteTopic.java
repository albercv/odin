package com.e2d.odin.application.topic;

import com.e2d.odin.application.port.TopicRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.topic.TopicId;
import java.time.Clock;

/** Borrado lógico: el tema deja de salir y la versión de la lista no cambia. */
public class DeleteTopic {

    private final TopicRepository topics;
    private final Clock clock;

    public DeleteTopic(TopicRepository topics, Clock clock) {
        this.topics = topics;
        this.clock = clock;
    }

    public void execute(Actor actor, CompanyId companyId, TopicId id) {
        AccessPolicy.requireAdminOf(actor, companyId);
        topics.find(companyId, id).orElseThrow(() -> new NotFoundException("Tema no encontrado"));
        topics.delete(companyId, id, clock.instant());
    }
}
