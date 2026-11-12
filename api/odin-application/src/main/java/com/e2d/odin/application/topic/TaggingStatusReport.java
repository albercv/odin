package com.e2d.odin.application.topic;

import com.e2d.odin.application.port.TaggingBacklog;
import com.e2d.odin.application.shared.TaggingWindow;

public record TaggingStatusReport(TaggingBacklog backlog, TaggingWindow window) {
}
