package com.e2d.odin.application.topic;

import com.e2d.odin.domain.topic.DocumentTopic;
import java.util.Optional;

public record DocumentTopicLine(DocumentTopic topic, String topicName, Optional<String> decidedByName) {
}
