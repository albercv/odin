package com.e2d.odin.application.topic;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryTaggingRepository;
import com.e2d.odin.application.fake.InMemoryTopicRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.audit.AuditOutcome;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.topic.Topic;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Una entrada por cambio del catálogo de temas o de los temas de un documento; el nombre del tema es etiqueta. */
class TopicAuditTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryTopicRepository topics = new InMemoryTopicRepository();
    private final InMemoryTaggingRepository tagging = new InMemoryTaggingRepository(orgs, docs, topics);
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final MutableClock clock = new MutableClock();
    private final CompanyId company = Fixtures.company(orgs);
    private final Actor admin = Actors.companyAdmin(company);

    private AuditEntry only(AuditAction action) {
        assertThat(audit.entries).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo(action));
        AuditEntry e = audit.entries.get(0);
        assertThat(e.outcome()).isEqualTo(AuditOutcome.DONE);
        assertThat(e.actorUserId()).contains(admin.userId());
        assertThat(e.actorRole()).contains("COMPANY_ADMIN");
        assertThat(e.company()).contains(company);
        return e;
    }

    private static AuditEntry.Target target(Topic t) {
        return new AuditEntry.Target("TOPIC", t.id().value().toString(), t.name());
    }

    private Topic existing() {
        Topic topic = new CreateTopic(orgs, topics, new SequenceIdGenerator(), new InMemoryAuditLog()).execute(admin,
                company, "Agua", "Fugas");
        return topic;
    }

    @Test
    void createTopicRecordsItsNameAsLabel() {
        Topic topic = new CreateTopic(orgs, topics, new SequenceIdGenerator(), audit).execute(admin, company, "Agua",
                "Fugas y humedades");

        AuditEntry e = only(AuditAction.TOPIC_CREATED);
        assertThat(e.target()).contains(target(topic));
        assertThat(e.details()).doesNotContainValue("Fugas y humedades");
    }

    @Test
    void updateTopicRecordsWhichFieldsChanged() {
        Topic topic = existing();

        Topic updated = new UpdateTopic(topics, audit).execute(admin, company, topic.id(),
                new TopicChanges(Optional.empty(), Optional.of("Otra definición")));

        AuditEntry e = only(AuditAction.TOPIC_UPDATED);
        assertThat(e.target()).contains(target(updated));
        assertThat(e.details()).containsEntry("nameChanged", false).containsEntry("definitionChanged", true);
    }

    @Test
    void deleteTopicRecordsIt() {
        Topic topic = existing();

        new DeleteTopic(topics, clock, audit).execute(admin, company, topic.id());

        assertThat(only(AuditAction.TOPIC_DELETED).target()).contains(target(topic));
    }

    @Test
    void addAndRemoveTopicRecordTheTopicAsTargetAndTheDocumentId() {
        Topic topic = existing();
        Document document = Fixtures.readyDocument(docs, company, "texto de la página");
        var review = new ReviewDocumentTopics(docs, topics, tagging, clock, audit);

        review.add(admin, document.id(), topic.id());
        AuditEntry added = only(AuditAction.DOCUMENT_TOPIC_ADDED);
        assertThat(added.target()).contains(target(topic));
        assertThat(added.details()).containsEntry("documentId", document.id().value().toString());

        audit.entries.clear();
        review.remove(admin, document.id(), topic.id());
        assertThat(only(AuditAction.DOCUMENT_TOPIC_REMOVED).target()).contains(target(topic));
    }

    @Test
    void retagRecordsTheDocumentAndNoText() {
        Document document = Fixtures.readyDocument(docs, company, "texto de la página");

        new ReviewDocumentTopics(docs, topics, tagging, clock, audit).retag(admin, document.id());

        AuditEntry e = only(AuditAction.DOCUMENT_RETAGGED);
        assertThat(e.target().orElseThrow().id()).isEqualTo(document.id().value().toString());
        assertThat(e.toString()).doesNotContain("texto de la página");
    }
}
