package com.e2d.odin.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.InMemoryTaggingRepository;
import com.e2d.odin.application.fake.InMemoryTopicRepository;
import com.e2d.odin.application.topic.TaggedDocument;
import com.e2d.odin.application.topic.TopicLabel;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicSource;
import com.e2d.odin.domain.topic.TopicState;
import java.util.UUID;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ListDocumentsTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(orgs);
    private final InMemoryTopicRepository topics = new InMemoryTopicRepository();
    private final InMemoryTaggingRepository tagging = new InMemoryTaggingRepository(orgs, docs, topics);
    private final ListDocuments list = new ListDocuments(orgs, members, docs, topics, tagging);
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);

    private List<Document> documents(Actor actor, Optional<ProjectId> project, int offset, Optional<Integer> limit) {
        return list.execute(actor, project, Optional.empty(), offset, limit).stream().map(t -> t.entry().document())
                .toList();
    }

    private List<DocumentEntry> entries(Actor actor, Optional<ProjectId> project, Optional<TopicId> topic, int offset,
            Optional<Integer> limit) {
        return list.execute(actor, project, topic, offset, limit).stream().map(TaggedDocument::entry).toList();
    }

    @Test
    void listsSupersededWithItsReplacement() {
        Document old = Fixtures.readyDocument(docs, companyA, "viejo");
        Document middle = Fixtures.readyDocument(docs, companyA, "medio");
        Document current = Fixtures.readyDocument(docs, companyA, "vigente");
        docs.markSuperseded(companyA, old.id(), middle.id(), Fixtures.NOW);
        docs.markSuperseded(companyA, middle.id(), current.id(), Fixtures.NOW);

        List<DocumentEntry> entries = entries(Actors.worker(companyA), Optional.empty(), Optional.empty(), 0, Optional.empty());

        DocumentEntry oldEntry = entries.stream().filter(e -> e.document().id().equals(old.id())).findFirst().orElseThrow();
        assertThat(oldEntry.document().superseded()).isTrue();
        assertThat(oldEntry.replacement()).contains(DocumentRef.of(current));
        assertThat(entries.stream().filter(e -> e.document().id().equals(current.id())).findFirst().orElseThrow()
                .replacement()).isEmpty();
    }

    @Test
    void replacementInAClosedProjectIsNotRevealed() {
        Document old = Fixtures.readyDocument(docs, companyA, "viejo");
        Document hidden = Fixtures.documentIn(docs, companyA, Fixtures.project(orgs, companyA), "oculto");
        docs.markSuperseded(companyA, old.id(), hidden.id(), Fixtures.NOW);

        List<DocumentEntry> entries = entries(Actors.worker(companyA), Optional.empty(), Optional.empty(), 0, Optional.empty());

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).document().superseded()).isTrue();
        assertThat(entries.get(0).replacement()).isEmpty();
        assertThat(entries.get(0).supersededBy()).isEmpty();
    }

    @Test
    void filtersByCaseDocument() {
        Document plain = Fixtures.readyDocument(docs, companyA, "suelto");
        Document marked = Fixtures.readyDocument(docs, companyA, "caso");
        docs.markCaseDocuments(companyA, java.util.Set.of(marked.id()), true);
        Actor worker = Actors.worker(companyA);

        assertThat(list.execute(worker, Optional.empty(), Optional.empty(), Optional.of(true), 0, Optional.empty())
                .stream().map(t -> t.entry().document().id())).containsExactly(marked.id());
        assertThat(list.execute(worker, Optional.empty(), Optional.empty(), Optional.of(false), 0, Optional.empty())
                .stream().map(t -> t.entry().document().id())).containsExactly(plain.id());
        assertThat(list.execute(worker, Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.empty()))
                .hasSize(2);
        assertThat(list.execute(worker, Optional.empty(), Optional.empty(), Optional.of(true), 0, Optional.empty())
                .get(0).entry().document().caseDocument()).isTrue();
    }

    @Test
    void caseFilterNeedsSearchCapabilityNotSimilarCases() {
        CompanyId onlySearch = Fixtures.companyWith(orgs, java.util.EnumSet.of(
                com.e2d.odin.domain.organization.Capability.SEARCH_KNOWLEDGE));
        Document marked = Fixtures.readyDocument(docs, onlySearch, "caso");
        docs.markCaseDocuments(onlySearch, java.util.Set.of(marked.id()), true);

        assertThat(list.execute(Actors.worker(onlySearch), Optional.empty(), Optional.empty(), Optional.of(true), 0,
                Optional.empty())).hasSize(1);
    }

    @Test
    void capsLimitAt500() {
        IntStream.range(0, 510).forEach(i -> Fixtures.document(docs, companyA, DocumentStatus.READY));

        assertThat(documents(Actors.worker(companyA), Optional.empty(), 0, Optional.of(1000))).hasSize(500);
        assertThat(documents(Actors.worker(companyA), Optional.empty(), 0, Optional.empty())).hasSize(100);
    }

    @Test
    void listsOnlyReadyDocuments() {
        Document ready = Fixtures.document(docs, companyA, DocumentStatus.READY);
        for (DocumentStatus other : List.of(DocumentStatus.RECEIVED, DocumentStatus.PROCESSING, DocumentStatus.FAILED,
                DocumentStatus.PENDING_READER)) {
            Fixtures.document(docs, companyA, other);
        }

        assertThat(documents(Actors.worker(companyA), Optional.empty(), 0, Optional.empty())).containsExactly(ready);
    }

    @Test
    void workerOfOtherCompanyNeverSeesCompanyADocuments() {
        Document a = Fixtures.document(docs, companyA, DocumentStatus.READY);

        assertThat(documents(Actors.worker(companyB), Optional.empty(), 0, Optional.empty())).doesNotContain(a);
        assertThat(docs.lastListScope()).isEqualTo(CompanyScope.of(companyB));
    }

    @Test
    void workerOfOtherCompanyCannotUseProjectOfCompanyA() {
        var projectOfA = Fixtures.project(orgs, companyA);

        assertThatThrownBy(() -> list.execute(Actors.worker(companyB), Optional.of(projectOfA), Optional.empty(), 0, Optional.empty()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void adminCannotUseWorkerListing() {
        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(companyA), Optional.empty(), Optional.empty(), 0, Optional.empty()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unassignedProjectIsInvisible() {
        ProjectId closed = Fixtures.project(orgs, companyA);
        Document inClosed = Fixtures.documentIn(docs, companyA, closed, "texto");
        Document withoutProject = Fixtures.readyDocument(docs, companyA, "libre");
        Actor worker = Actors.worker(companyA);

        assertThat(documents(worker, Optional.empty(), 0, Optional.empty())).containsExactly(withoutProject);
        assertThatThrownBy(() -> list.execute(worker, Optional.of(closed), Optional.empty(), 0, Optional.empty()))
                .isInstanceOf(NotFoundException.class).hasMessage("Proyecto no encontrado");

        members.add(closed, worker.userId());
        assertThat(documents(worker, Optional.empty(), 0, Optional.empty()))
                .containsExactlyInAnyOrder(inClosed, withoutProject);
        assertThat(documents(worker, Optional.of(closed), 0, Optional.empty())).containsExactly(inClosed);
    }

    @Test
    void companyWideProjectDocumentsAreVisibleToEveryWorker() {
        ProjectId open = Fixtures.companyWideProject(orgs, companyA);
        Document inOpen = Fixtures.documentIn(docs, companyA, open, "texto");

        assertThat(documents(Actors.worker(companyA), Optional.of(open), 0, Optional.empty()))
                .containsExactly(inOpen);
    }

    @Test
    void documentOfDeletedProjectStaysVisibleToWhoCouldSeeIt() {
        ProjectId closed = Fixtures.project(orgs, companyA);
        Document inClosed = Fixtures.documentIn(docs, companyA, closed, "texto");
        Actor member = Actors.worker(companyA);
        members.add(closed, member.userId());
        orgs.deleteProject(companyA, closed, Fixtures.NOW);

        assertThat(documents(member, Optional.empty(), 0, Optional.empty())).containsExactly(inClosed);
        assertThat(documents(Actors.worker(companyA), Optional.empty(), 0, Optional.empty())).isEmpty();
        assertThatThrownBy(() -> list.execute(member, Optional.of(closed), Optional.empty(), 0, Optional.empty()))
                .isInstanceOf(NotFoundException.class);
    }

    private Topic topic(CompanyId company, String name) {
        Topic topic = new Topic(new TopicId(UUID.randomUUID()), company, name, "Definición de " + name);
        topics.save(topic);
        return topic;
    }

    @Test
    void filtersByTopicWithActiveTopicsOnly() {
        Topic water = topic(companyA, "Agua");
        Topic insurance = topic(companyA, "Seguros");
        Document aiTagged = Fixtures.readyDocument(docs, companyA, "uno");
        Document adminTagged = Fixtures.readyDocument(docs, companyA, "dos");
        Document removed = Fixtures.readyDocument(docs, companyA, "tres");
        Fixtures.readyDocument(docs, companyA, "cuatro");
        tagging.seed(aiTagged.id(), water.id(), TopicSource.AI, TopicState.ACTIVE);
        tagging.seed(aiTagged.id(), insurance.id(), TopicSource.AI, TopicState.ACTIVE);
        tagging.seed(adminTagged.id(), water.id(), TopicSource.ADMIN, TopicState.ACTIVE);
        tagging.seed(removed.id(), water.id(), TopicSource.AI, TopicState.REMOVED);

        List<TaggedDocument> byWater = list.execute(Actors.worker(companyA), Optional.empty(),
                Optional.of(water.id()), 0, Optional.empty());

        assertThat(byWater).extracting(t -> t.entry().document()).containsExactlyInAnyOrder(aiTagged, adminTagged);
        TaggedDocument first = byWater.stream().filter(t -> t.entry().document().equals(aiTagged)).findFirst()
                .orElseThrow();
        assertThat(first.topics()).containsExactlyInAnyOrder(new TopicLabel(water.id(), "Agua", TopicSource.AI),
                new TopicLabel(insurance.id(), "Seguros", TopicSource.AI));
    }

    @Test
    void foreignTopicIsNotFound() {
        Topic foreign = topic(companyB, "Ajeno");

        assertThatThrownBy(() -> list.execute(Actors.worker(companyA), Optional.empty(), Optional.of(foreign.id()), 0,
                Optional.empty())).isInstanceOf(NotFoundException.class).hasMessage("Tema no encontrado");
        assertThatThrownBy(() -> list.execute(Actors.worker(companyA), Optional.empty(),
                Optional.of(new TopicId(UUID.randomUUID())), 0, Optional.empty()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void restrictedProjectOfOthersIsInvisible() {
        Topic water = topic(companyA, "Agua");
        ProjectId closed = Fixtures.project(orgs, companyA);
        Document hidden = Fixtures.documentIn(docs, companyA, closed, "secreto");
        Document visible = Fixtures.readyDocument(docs, companyA, "libre");
        tagging.seed(hidden.id(), water.id(), TopicSource.AI, TopicState.ACTIVE);
        tagging.seed(visible.id(), water.id(), TopicSource.AI, TopicState.ACTIVE);
        Actor outsider = Actors.worker(companyA);

        assertThat(list.execute(outsider, Optional.empty(), Optional.of(water.id()), 0, Optional.empty()))
                .extracting(t -> t.entry().document()).containsExactly(visible);
        assertThatThrownBy(() -> list.execute(outsider, Optional.of(closed), Optional.of(water.id()), 0,
                Optional.empty())).isInstanceOf(NotFoundException.class);
    }
}
