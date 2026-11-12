package com.e2d.odin.application.port;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.topic.Topic;
import java.util.List;

/** Un documento tomado para etiquetar, con su texto y la lista de temas de su empresa en ese momento. */
public record TaggingJob(Document document, List<DocumentPage> pages, List<Topic> topics, String companyName,
        int topicListVersion, int attemptCount) {
}
