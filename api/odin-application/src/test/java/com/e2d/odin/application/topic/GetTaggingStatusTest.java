package com.e2d.odin.application.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryTaggingRepository;
import com.e2d.odin.application.fake.InMemoryTopicRepository;
import com.e2d.odin.application.shared.TaggingWindow;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.topic.TaggingStatus;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetTaggingStatusTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryTopicRepository topics = new InMemoryTopicRepository();
    private final InMemoryTaggingRepository tagging = new InMemoryTaggingRepository(orgs, docs, topics);
    private final TaggingWindow window = TaggingWindow.between(LocalTime.of(22, 0), LocalTime.of(7, 0),
            ZoneId.of("Europe/Madrid"));
    private final GetTaggingStatus status = new GetTaggingStatus(tagging, window);
    private final CompanyId company = Fixtures.company(orgs);

    @Test
    void returnsBacklogAndWindow() {
        topics.save(new Topic(new TopicId(UUID.randomUUID()), company, "Agua", "Fugas"));
        var pending = Fixtures.readyDocument(docs, company, "uno");
        var done = Fixtures.readyDocument(docs, company, "dos");
        tagging.seedRow(done, TaggingStatus.DONE);

        TaggingStatusReport report = status.execute(Actors.companyAdmin(company), company);

        assertThat(report.backlog().pendingDocuments()).isEqualTo(1);
        assertThat(report.backlog().oldestPendingSince()).contains(pending.uploadedAt());
        assertThat(report.window()).isEqualTo(window);
    }

    @Test
    void groupAdminSeesAnyCompany() {
        assertThat(status.execute(Actors.groupAdmin(), company).backlog().pendingDocuments()).isZero();
    }

    @Test
    void workerIsForbidden() {
        assertThatThrownBy(() -> status.execute(Actors.worker(company), company))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        assertThatThrownBy(() -> status.execute(Actors.companyAdmin(Fixtures.company(orgs)), company))
                .isInstanceOf(ForbiddenException.class);
    }
}
