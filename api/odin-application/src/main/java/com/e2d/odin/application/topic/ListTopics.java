package com.e2d.odin.application.topic;

import com.e2d.odin.application.port.TopicRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.topic.Topic;
import java.util.List;

public class ListTopics {

    private final TopicRepository topics;

    public ListTopics(TopicRepository topics) {
        this.topics = topics;
    }

    public List<Topic> execute(Actor actor, CompanyId companyId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        return topics.list(companyId);
    }
}
