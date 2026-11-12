package com.e2d.odin.application.fake;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.shared.TaggingSettings;
import com.e2d.odin.application.shared.TaggingWindow;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicState;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El doble tiene que comportarse como la base ante datos de otra empresa. */
class InMemoryTaggingRepositoryTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryTopicRepository topics = new InMemoryTopicRepository();
    private final InMemoryTaggingRepository tagging = new InMemoryTaggingRepository(orgs, docs, topics);

    private Topic topic(CompanyId company, String name) {
        Topic topic = new Topic(new TopicId(UUID.randomUUID()), company, name, "Definición de " + name);
        topics.save(topic);
        return topic;
    }

    @Test
    void decideWithTheScopeOfAnotherCompanyWritesNothing() {
        CompanyId a = Fixtures.company(orgs);
        CompanyId b = Fixtures.company(orgs);
        Topic ofA = topic(a, "Agua");
        Document docOfA = Fixtures.readyDocument(docs, a, "uno");

        tagging.decide(CompanyScope.of(b), docOfA.id(), ofA.id(), TopicState.ACTIVE, new UserId(UUID.randomUUID()),
                Fixtures.NOW);

        assertThat(tagging.topicsOf(CompanyScope.all(), docOfA.id())).isEmpty();
    }

    @Test
    void saveResultWithATopicOfAnotherCompanyStoresNothing() {
        CompanyId a = Fixtures.company(orgs);
        CompanyId b = Fixtures.company(orgs);
        Topic ofA = topic(a, "Agua");
        topic(b, "Seguros");
        Document docOfB = Fixtures.readyDocument(docs, b, "dos");
        var settings = new TaggingSettings(TaggingWindow.always(ZoneOffset.UTC), Duration.ofMinutes(5), 6000, 3);
        tagging.claimNext(Fixtures.NOW, settings).orElseThrow();

        tagging.saveResult(docOfB.id(), Set.of(ofA.id()), "modelo", Fixtures.NOW);

        assertThat(tagging.topicsOf(CompanyScope.all(), docOfB.id())).isEmpty();
    }
}
