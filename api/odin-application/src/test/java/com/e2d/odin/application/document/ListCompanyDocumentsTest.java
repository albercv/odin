package com.e2d.odin.application.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryTaggingRepository;
import com.e2d.odin.application.fake.InMemoryTopicRepository;
import com.e2d.odin.application.topic.TaggedDocument;
import com.e2d.odin.application.topic.TopicLabel;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.topic.TaggingStatus;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicSource;
import com.e2d.odin.domain.topic.TopicState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListCompanyDocumentsTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryTopicRepository topics = new InMemoryTopicRepository();
    private final InMemoryTaggingRepository tagging = new InMemoryTaggingRepository(orgs, docs, topics);
    private final ListCompanyDocuments list = new ListCompanyDocuments(docs, topics, tagging);
    private final CompanyId company = Fixtures.company(orgs);

    @Test
    void includesTopicsAndTaggingStatus() {
        Topic water = new Topic(new TopicId(UUID.randomUUID()), company, "Agua", "Fugas");
        Topic insurance = new Topic(new TopicId(UUID.randomUUID()), company, "Seguros", "Pólizas");
        topics.save(water);
        topics.save(insurance);
        Document tagged = Fixtures.readyDocument(docs, company, "uno");
        Document plain = Fixtures.readyDocument(docs, company, "dos");
        tagging.seed(tagged.id(), water.id(), TopicSource.AI, TopicState.ACTIVE);
        tagging.seed(tagged.id(), insurance.id(), TopicSource.ADMIN, TopicState.REMOVED);
        tagging.seedRow(tagged, TaggingStatus.DONE);

        List<TaggedDocument> result = list.execute(Actors.companyAdmin(company), company, 0, 100);

        TaggedDocument first = result.stream().filter(t -> t.entry().document().id().equals(tagged.id()))
                .findFirst().orElseThrow();
        assertThat(first.topics()).containsExactly(new TopicLabel(water.id(), "Agua", TopicSource.AI));
        assertThat(first.tagging()).get().extracting(t -> t.status()).isEqualTo(TaggingStatus.DONE);
        TaggedDocument second = result.stream().filter(t -> t.entry().document().id().equals(plain.id()))
                .findFirst().orElseThrow();
        assertThat(second.topics()).isEmpty();
        assertThat(second.tagging()).isEmpty();
    }
}
