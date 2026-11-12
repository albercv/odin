package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.TopicRepository;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryTopicRepository implements TopicRepository {

    private final Map<TopicId, Topic> topics = new LinkedHashMap<>();
    private final Map<CompanyId, Integer> versions = new HashMap<>();

    @Override
    public List<Topic> list(CompanyId companyId) {
        return topics.values().stream().filter(t -> t.companyId().equals(companyId))
                .sorted(Comparator.comparing((Topic t) -> t.name().toLowerCase()).thenComparing(t -> t.id().value()))
                .toList();
    }

    @Override
    public Optional<Topic> find(CompanyId companyId, TopicId topicId) {
        return Optional.ofNullable(topics.get(topicId)).filter(t -> t.companyId().equals(companyId));
    }

    @Override
    public void save(Topic topic) {
        Topic stored = topics.put(topic.id(), topic);
        if (!topic.equals(stored)) {
            versions.merge(topic.companyId(), 1, Integer::sum);
        }
    }

    @Override
    public void delete(CompanyId companyId, TopicId topicId, Instant at) {
        find(companyId, topicId).ifPresent(t -> topics.remove(topicId));
    }

    @Override
    public int listVersion(CompanyId companyId) {
        return versions.getOrDefault(companyId, 0);
    }
}
