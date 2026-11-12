package com.e2d.odin.application.topic;

import java.util.Optional;

public record TopicChanges(Optional<String> name, Optional<String> definition) {
}
