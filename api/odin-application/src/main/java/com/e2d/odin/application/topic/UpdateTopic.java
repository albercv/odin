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
import java.util.Optional;

/** Cambia el nombre o la definición. Es el mismo tema, así que las decisiones sobre él se conservan. */
public class UpdateTopic {

    private final TopicRepository topics;
    private final AuditLog audit;

    public UpdateTopic(TopicRepository topics, AuditLog audit) {
        this.topics = topics;
        this.audit = audit;
    }

    public Topic execute(Actor actor, CompanyId companyId, TopicId id, TopicChanges changes) {
        AccessPolicy.requireAdminOf(actor, companyId);
        Topic topic = topics.find(companyId, id).orElseThrow(() -> new NotFoundException("Tema no encontrado"));
        String name = changes.name().map(TopicRules::requireName).orElse(topic.name());
        String definition = changes.definition().map(TopicRules::requireDefinition).orElse(topic.definition());
        TopicRules.requireCompatibleName(topics.list(companyId), Optional.of(id), name);
        Topic updated = new Topic(id, companyId, name, definition);
        topics.save(updated);
        audit.record(AuditEntry.by(actor, AuditAction.TOPIC_UPDATED).onCompany(companyId)
                .on("TOPIC", id.value().toString(), name).with("nameChanged", !name.equals(topic.name()))
                .with("definitionChanged", !definition.equals(topic.definition())));
        return updated;
    }
}
