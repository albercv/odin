package com.e2d.odin.application.topic;

import com.e2d.odin.application.fake.InMemoryAuditLog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryTaggingRepository;
import com.e2d.odin.application.fake.InMemoryTopicRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.topic.DocumentTopic;
import com.e2d.odin.domain.topic.TaggingStatus;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicSource;
import com.e2d.odin.domain.topic.TopicState;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewDocumentTopicsTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryTopicRepository topics = new InMemoryTopicRepository();
    private final InMemoryTaggingRepository tagging = new InMemoryTaggingRepository(orgs, docs, topics);
    private final MutableClock clock = new MutableClock();
    private final ReviewDocumentTopics review = new ReviewDocumentTopics(docs, topics, tagging, clock, new InMemoryAuditLog());
    private final CompanyId company = Fixtures.company(orgs);
    private final Actor admin = Actors.companyAdmin(company);
    private final Topic water = topic(company, "Agua");
    private final Document document = Fixtures.readyDocument(docs, company, "texto");

    private Topic topic(CompanyId owner, String name) {
        Topic topic = new Topic(new TopicId(UUID.randomUUID()), owner, name, "Definición de " + name);
        topics.save(topic);
        return topic;
    }

    private DocumentTopic stored(Topic topic) {
        return tagging.topicsOf(CompanyScope.all(), document.id()).stream()
                .filter(t -> t.topicId().equals(topic.id())).findFirst().orElseThrow();
    }

    @Test
    void addsAdminTopic() {
        review.add(admin, document.id(), water.id());

        DocumentTopic added = stored(water);
        assertThat(added.source()).isEqualTo(TopicSource.ADMIN);
        assertThat(added.state()).isEqualTo(TopicState.ACTIVE);
        assertThat(added.decidedBy()).contains(admin.userId());
        assertThat(added.decidedAt()).contains(clock.instant());
    }

    @Test
    void removesAiTopic() {
        tagging.seed(document.id(), water.id(), TopicSource.AI, TopicState.ACTIVE);

        review.remove(admin, document.id(), water.id());

        DocumentTopic removed = stored(water);
        assertThat(removed.state()).isEqualTo(TopicState.REMOVED);
        assertThat(removed.source()).isEqualTo(TopicSource.AI);
        assertThat(removed.decidedBy()).contains(admin.userId());
        assertThat(removed.decidedAt()).contains(clock.instant());
    }

    @Test
    void readdingRemovedTopicMakesItAdminActive() {
        tagging.seed(document.id(), water.id(), TopicSource.AI, TopicState.ACTIVE);
        review.remove(admin, document.id(), water.id());

        review.add(admin, document.id(), water.id());

        assertThat(stored(water).source()).isEqualTo(TopicSource.ADMIN);
        assertThat(stored(water).state()).isEqualTo(TopicState.ACTIVE);
    }

    @Test
    void retagRequeues() {
        tagging.seedRow(document, TaggingStatus.DONE);

        review.retag(admin, document.id());

        assertThat(tagging.rowOf(document.id())).isEmpty();
    }

    @Test
    void groupAdminReviewsAnyCompany() {
        review.add(Actors.groupAdmin(), document.id(), water.id());

        assertThat(stored(water).state()).isEqualTo(TopicState.ACTIVE);
    }

    @Test
    void topicOfAnotherCompanyIsNotFound() {
        Topic foreign = topic(Fixtures.company(orgs), "Ajeno");

        assertThatThrownBy(() -> review.add(admin, document.id(), foreign.id()))
                .isInstanceOf(NotFoundException.class).hasMessage("Tema no encontrado");
        assertThat(tagging.topicsOf(CompanyScope.all(), document.id())).isEmpty();
    }

    @Test
    void missingDocumentIsNotFound() {
        assertThatThrownBy(() -> review.retag(admin, new com.e2d.odin.domain.shared.DocumentId(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void workerIsForbidden() {
        Actor worker = Actors.worker(company);

        assertThatThrownBy(() -> review.add(worker, document.id(), water.id())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> review.retag(worker, document.id())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminOfAnotherCompanyCannotReachTheDocument() {
        Actor other = Actors.companyAdmin(Fixtures.company(orgs));
        tagging.seedRow(document, TaggingStatus.DONE);

        assertThatThrownBy(() -> review.add(other, document.id(), water.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> review.remove(other, document.id(), water.id()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> review.retag(other, document.id())).isInstanceOf(NotFoundException.class);
        assertThat(tagging.rowOf(document.id())).isPresent();
        assertThat(tagging.topicsOf(CompanyScope.all(), document.id())).isEmpty();
    }
}
