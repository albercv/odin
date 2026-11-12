package com.e2d.odin.application.topic;

import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.TopicRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ValidationException;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import java.util.List;
import java.util.Optional;

public class CreateTopic {

    private final OrganizationRepository organizations;
    private final TopicRepository topics;
    private final IdGenerator ids;

    public CreateTopic(OrganizationRepository organizations, TopicRepository topics, IdGenerator ids) {
        this.organizations = organizations;
        this.topics = topics;
        this.ids = ids;
    }

    public Topic execute(Actor actor, CompanyId companyId, String name, String definition) {
        AccessPolicy.requireAdminOf(actor, companyId);
        String topicName = TopicRules.requireName(name);
        String topicDefinition = TopicRules.requireDefinition(definition);
        organizations.findCompany(companyId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        List<Topic> existing = topics.list(companyId);
        TopicRules.requireCompatibleName(existing, Optional.empty(), topicName);
        if (existing.size() >= Topic.MAX_PER_COMPANY) {
            throw new ValidationException("Una empresa puede tener como mucho " + Topic.MAX_PER_COMPANY + " temas");
        }
        Topic topic = new Topic(new TopicId(ids.newId()), companyId, topicName, topicDefinition);
        topics.save(topic);
        return topic;
    }
}
