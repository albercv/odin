package com.e2d.odin.application.topic;

import com.e2d.odin.domain.topic.DocumentTagging;
import java.util.List;
import java.util.Optional;

public record DocumentTopicsReport(List<DocumentTopicLine> topics, Optional<DocumentTagging> tagging) {
}
