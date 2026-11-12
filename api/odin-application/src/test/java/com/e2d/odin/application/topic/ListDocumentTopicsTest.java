package com.e2d.odin.application.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryTaggingRepository;
import com.e2d.odin.application.fake.InMemoryTopicRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.topic.TaggingStatus;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicSource;
import com.e2d.odin.domain.topic.TopicState;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListDocumentTopicsTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryTopicRepository topics = new InMemoryTopicRepository();
    private final InMemoryTaggingRepository tagging = new InMemoryTaggingRepository(orgs, docs, topics);
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final ListDocumentTopics list = new ListDocumentTopics(docs, topics, tagging, users);
    private final CompanyId company = Fixtures.company(orgs);
    private final Document document = Fixtures.readyDocument(docs, company, "texto");

    private Topic topic(String name) {
        Topic topic = new Topic(new TopicId(UUID.randomUUID()), company, name, "Definición de " + name);
        topics.save(topic);
        return topic;
    }

    @Test
    void listsActiveAndRemovedWithTaggingState() {
        Topic water = topic("Agua");
        Topic insurance = topic("Seguros");
        UserId admin = People.member(users, memberships, new UserId(UUID.randomUUID()), "Carlos", Optional.empty(),
                Role.COMPANY_ADMIN, company, true, Optional.empty()).id();
        tagging.seed(document.id(), water.id(), TopicSource.AI, TopicState.ACTIVE);
        tagging.seed(document.id(), insurance.id(), TopicSource.AI, TopicState.ACTIVE);
        tagging.decide(CompanyScope.all(), document.id(), insurance.id(), TopicState.REMOVED, admin,
                Fixtures.NOW);
        tagging.seedRow(document, TaggingStatus.DONE);

        DocumentTopicsReport report = list.execute(Actors.companyAdmin(company), document.id());

        assertThat(report.topics()).extracting(DocumentTopicLine::topicName).containsExactly("Agua", "Seguros");
        assertThat(report.topics()).extracting(line -> line.topic().state())
                .containsExactly(TopicState.ACTIVE, TopicState.REMOVED);
        assertThat(report.topics().get(0).decidedByName()).isEmpty();
        assertThat(report.topics().get(1).decidedByName()).contains("Carlos Pérez");
        assertThat(report.tagging()).get().extracting(t -> t.status()).isEqualTo(TaggingStatus.DONE);
    }

    @Test
    void documentWithoutTaggingHasNoState() {
        assertThat(list.execute(Actors.groupAdmin(), document.id()).tagging()).isEmpty();
    }

    @Test
    void workerIsForbidden() {
        Actor worker = Actors.worker(company);

        assertThatThrownBy(() -> list.execute(worker, document.id())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminOfAnotherCompanyCannotReachTheDocument() {
        Actor other = Actors.companyAdmin(Fixtures.company(orgs));

        assertThatThrownBy(() -> list.execute(other, document.id())).isInstanceOf(NotFoundException.class);
    }
}
