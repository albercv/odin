package com.e2d.odin.application.topic;

import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.application.port.TopicRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import java.time.Clock;

/** Borrado lógico: el tema deja de salir y la versión de la lista no cambia. */
public class DeleteTopic {

    private final TopicRepository topics;
    private final Clock clock;
    private final AuditLog audit;

    public DeleteTopic(TopicRepository topics, Clock clock, AuditLog audit) {
        this.topics = topics;
        this.clock = clock;
        this.audit = audit;
    }

    public void execute(Actor actor, CompanyId companyId, TopicId id) {
        AccessPolicy.requireAdminOf(actor, companyId);
        Topic topic = topics.find(companyId, id).orElseThrow(() -> new NotFoundException("Tema no encontrado"));
        topics.delete(companyId, id, clock.instant());
        audit.record(AuditEntry.by(actor, AuditAction.TOPIC_DELETED).onCompany(companyId)
                .on("TOPIC", id.value().toString(), topic.name()));
    }
}
