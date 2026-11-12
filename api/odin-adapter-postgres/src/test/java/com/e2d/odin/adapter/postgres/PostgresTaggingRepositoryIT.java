package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.port.TaggingBacklog;
import com.e2d.odin.application.port.TaggingJob;
import com.e2d.odin.application.shared.TaggingSettings;
import com.e2d.odin.application.shared.TaggingWindow;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.topic.DocumentTopic;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicSource;
import com.e2d.odin.domain.topic.TopicState;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class PostgresTaggingRepositoryIT extends AbstractPostgresIT {

    private static final TaggingSettings SETTINGS = new TaggingSettings(TaggingWindow.always(ZoneOffset.UTC),
            Duration.ofMinutes(5), 6000, 3);
    private static final String V1 = "modelo; instrucciones v1";

    private final PostgresTopicRepository topics = new PostgresTopicRepository(tx);
    private final PostgresTaggingRepository tagging = new PostgresTaggingRepository(tx);
    private final JdbcClient owner = JdbcClient.create(Db.owner());

    private Topic topic(CompanyId company, String name) {
        Topic topic = new Topic(new TopicId(UUID.randomUUID()), company, name, "Definición de " + name);
        topics.save(topic);
        return topic;
    }

    private Document readyDocument(CompanyId company, Optional<ProjectId> project, String title) {
        UserId user = newUser(company);
        Document d = new Document(new DocumentId(UUID.randomUUID()), company, project, title, "f.pdf",
                DocumentFormat.PDF, UUID.randomUUID().toString(), DocumentStatus.READY, Optional.empty(), true, user,
                NOW, Optional.empty(), 0, Optional.empty(), Optional.empty());
        docs.save(d);
        docs.replacePages(d.id(), List.of(new DocumentPage(d.id(), 1, "Texto de " + title, "lector", "1")));
        return d;
    }

    private Document readyDocument(CompanyId company, String title) {
        return readyDocument(company, Optional.empty(), title);
    }

    private TaggingJob claim(Instant now) {
        return tagging.claimNext(now, SETTINGS).orElseThrow();
    }

    private String statusOf(Document d) {
        return owner.sql("SELECT status FROM document_tagging WHERE document_id = :id").param("id", d.id().value())
                .query(String.class).optional().orElse("SIN FILA");
    }

    private int attemptsOf(Document d) {
        return owner.sql("SELECT attempt_count FROM document_tagging WHERE document_id = :id")
                .param("id", d.id().value()).query(Integer.class).single();
    }

    private Map<TopicId, DocumentTopic> topicsOf(Document d) {
        return tagging.topicsOf(CompanyScope.all(), d.id()).stream()
                .collect(java.util.stream.Collectors.toMap(DocumentTopic::topicId, t -> t));
    }

    private void tagDone(Document d, Instant now, TopicId... ids) {
        assertThat(claim(now).document().id()).isEqualTo(d.id());
        tagging.saveResult(d.id(), Set.of(ids), V1, now);
    }

    @Test
    void claimsUntaggedBeforeOutdated() {
        CompanyId company = newCompany();
        Topic water = topic(company, "Agua");
        Document outdated = readyDocument(company, "Antiguo");
        tagDone(outdated, NOW, water.id());
        topic(company, "Seguros");
        Document untagged = readyDocument(company, "Nuevo");

        TaggingJob first = claim(NOW);
        TaggingJob second = claim(NOW);

        assertThat(first.document().id()).isEqualTo(untagged.id());
        assertThat(second.document().id()).isEqualTo(outdated.id());
        assertThat(second.topicListVersion()).isEqualTo(2);
        assertThat(second.attemptCount()).isEqualTo(1);
        assertThat(second.topics()).extracting(Topic::name).containsExactly("Agua", "Seguros");
        assertThat(second.pages()).extracting(DocumentPage::text).containsExactly("Texto de Antiguo");
        assertThat(second.companyName()).startsWith("Empresa ");
        assertThat(tagging.claimNext(NOW, SETTINGS)).isEmpty();
    }

    @Test
    void doesNotClaimCompaniesWithoutTopicsOrDocumentsNotReady() {
        CompanyId withoutTopics = newCompany();
        readyDocument(withoutTopics, "Sin temas");
        CompanyId company = newCompany();
        topic(company, "Agua");
        newDocument(company, newUser(company), "Recibido", DocumentStatus.RECEIVED);

        assertThat(tagging.claimNext(NOW, SETTINGS)).isEmpty();
    }

    @Test
    void skipsLockedRows() throws SQLException {
        CompanyId company = newCompany();
        topic(company, "Agua");
        Document locked = readyDocument(company, "Bloqueado");
        Document free = readyDocument(company, "Libre");

        try (Connection c = Db.owner().getConnection()) {
            c.setAutoCommit(false);
            c.createStatement().execute("SELECT id FROM document WHERE id = '" + locked.id().value() + "' FOR UPDATE");

            assertThat(claim(NOW).document().id()).isEqualTo(free.id());
            assertThat(tagging.claimNext(NOW, SETTINGS)).isEmpty();
            c.rollback();
        }
        assertThat(claim(NOW).document().id()).isEqualTo(locked.id());
    }

    @Test
    void retakesExpiredTagging() {
        CompanyId company = newCompany();
        topic(company, "Agua");
        Document d = readyDocument(company, "Largo");
        claim(NOW);

        assertThat(tagging.claimNext(NOW.plus(Duration.ofMinutes(9)), SETTINGS)).isEmpty();
        TaggingJob retaken = claim(NOW.plus(Duration.ofMinutes(11)));

        assertThat(retaken.document().id()).isEqualTo(d.id());
        assertThat(retaken.attemptCount()).isEqualTo(2);
    }

    @Test
    void deadlineGrowsWithTheNumberOfChunks() {
        CompanyId company = newCompany();
        topic(company, "Agua");
        Document d = readyDocument(company, "Largo");
        docs.replacePages(d.id(), List.of(new DocumentPage(d.id(), 1, "a".repeat(5000), "l", "1"),
                new DocumentPage(d.id(), 2, "b".repeat(5000), "l", "1"), new DocumentPage(d.id(), 3, "c".repeat(5000), "l", "1")));

        claim(NOW);

        // 3 tramos de 5 minutos y 5 más
        assertThat(owner.sql("SELECT deadline_at FROM document_tagging WHERE document_id = :id")
                .param("id", d.id().value()).query(java.time.OffsetDateTime.class).single().toInstant())
                .isEqualTo(NOW.plus(Duration.ofMinutes(20)));
    }

    @Test
    void failedWithCurrentVersionIsNotRetaken() {
        CompanyId company = newCompany();
        topic(company, "Agua");
        Document d = readyDocument(company, "Roto");
        claim(NOW);

        tagging.markFailed(d.id(), "demasiados intentos");

        assertThat(statusOf(d)).isEqualTo("FAILED");
        assertThat(tagging.claimNext(NOW.plus(Duration.ofDays(1)), SETTINGS)).isEmpty();
    }

    @Test
    void failedWithOldVersionIsRetaken() {
        CompanyId company = newCompany();
        topic(company, "Agua");
        Document d = readyDocument(company, "Roto");
        claim(NOW);
        tagging.markFailed(d.id(), "demasiados intentos");
        topic(company, "Seguros");

        TaggingJob retaken = claim(NOW);

        assertThat(retaken.document().id()).isEqualTo(d.id());
        assertThat(retaken.attemptCount()).isEqualTo(1);
        assertThat(statusOf(d)).isEqualTo("TAGGING");
    }

    @Test
    void returnToQueueKeepsOrReturnsTheAttempt() {
        CompanyId company = newCompany();
        topic(company, "Agua");
        Document d = readyDocument(company, "Cola");
        claim(NOW);

        tagging.returnToQueue(d.id(), true);
        assertThat(statusOf(d)).isEqualTo("PENDING");
        assertThat(attemptsOf(d)).isEqualTo(1);
        assertThat(claim(NOW).attemptCount()).isEqualTo(2);
        tagging.returnToQueue(d.id(), false);
        assertThat(attemptsOf(d)).isEqualTo(1);
    }

    @Test
    void saveResultKeepsAdminDecisions() {
        CompanyId company = newCompany();
        Topic water = topic(company, "Agua");
        Topic insurance = topic(company, "Seguros");
        Topic lifts = topic(company, "Ascensores");
        Topic garden = topic(company, "Jardín");
        Document d = readyDocument(company, "Póliza");
        UserId admin = newUser(company);
        tagDone(d, NOW, water.id(), insurance.id(), garden.id());
        tagging.decide(CompanyScope.of(company), d.id(), insurance.id(), TopicState.REMOVED, admin, NOW);
        tagging.decide(CompanyScope.of(company), d.id(), lifts.id(), TopicState.ACTIVE, admin, NOW);
        topic(company, "Otros");

        claim(NOW);
        tagging.saveResult(d.id(), Set.of(water.id(), insurance.id()), V1, NOW);

        Map<TopicId, DocumentTopic> saved = topicsOf(d);
        assertThat(saved).containsOnlyKeys(water.id(), insurance.id(), lifts.id());
        assertThat(saved.get(insurance.id()).state()).isEqualTo(TopicState.REMOVED);
        assertThat(saved.get(insurance.id()).source()).isEqualTo(TopicSource.AI);
        assertThat(saved.get(insurance.id()).decidedBy()).contains(admin);
        assertThat(saved.get(lifts.id()).source()).isEqualTo(TopicSource.ADMIN);
        assertThat(saved.get(lifts.id()).state()).isEqualTo(TopicState.ACTIVE);
        assertThat(saved.get(water.id()).source()).isEqualTo(TopicSource.AI);
        assertThat(saved.get(water.id()).taggerVersion()).contains(V1);
        assertThat(saved.get(water.id()).decidedBy()).isEmpty();
        assertThat(statusOf(d)).isEqualTo("DONE");
    }

    @Test
    void readdingARemovedTopicMakesItAdminAndActive() {
        CompanyId company = newCompany();
        Topic water = topic(company, "Agua");
        Document d = readyDocument(company, "Póliza");
        UserId admin = newUser(company);
        tagDone(d, NOW, water.id());

        tagging.decide(CompanyScope.of(company), d.id(), water.id(), TopicState.REMOVED, admin, NOW);
        tagging.decide(CompanyScope.of(company), d.id(), water.id(), TopicState.ACTIVE, admin, NOW.plusSeconds(60));

        DocumentTopic readded = topicsOf(d).get(water.id());
        assertThat(readded.state()).isEqualTo(TopicState.ACTIVE);
        assertThat(readded.source()).isEqualTo(TopicSource.ADMIN);
        assertThat(readded.taggerVersion()).isEmpty();
        assertThat(readded.decidedAt()).contains(NOW.plusSeconds(60));
    }

    @Test
    void adminDecisionDuringTaggingSurvives() throws Exception {
        CompanyId company = newCompany();
        Topic water = topic(company, "Agua");
        Document d = readyDocument(company, "Póliza");
        UserId admin = newUser(company);
        claim(NOW);

        // Una conexión tiene la fila bloqueada, como un guardado en curso; la decisión y el guardado esperan a la vez.
        try (Connection c = Db.owner().getConnection()) {
            c.setAutoCommit(false);
            c.createStatement().execute("SELECT 1 FROM document_tagging WHERE document_id = '" + d.id().value()
                    + "' FOR UPDATE");
            CompletableFuture<Void> decision = CompletableFuture.runAsync(() -> tagging.decide(
                    CompanyScope.of(company), d.id(), water.id(), TopicState.REMOVED, admin, NOW));
            CompletableFuture<Void> save = CompletableFuture.runAsync(() -> tagging.saveResult(d.id(),
                    Set.of(water.id()), V1, NOW));
            Thread.sleep(300);
            assertThat(decision).isNotDone();
            assertThat(save).isNotDone();
            c.commit();
            CompletableFuture.allOf(decision, save).get();
        }

        DocumentTopic result = topicsOf(d).get(water.id());
        assertThat(result.state()).isEqualTo(TopicState.REMOVED);
        assertThat(result.decidedBy()).contains(admin);
        assertThat(statusOf(d)).isEqualTo("DONE");
    }

    @Test
    void decisionOnAnUntaggedDocumentKeepsItInTheQueue() {
        CompanyId company = newCompany();
        Topic water = topic(company, "Agua");
        Document d = readyDocument(company, "Nuevo");

        tagging.decide(CompanyScope.of(company), d.id(), water.id(), TopicState.ACTIVE, newUser(company), NOW);

        assertThat(topicsOf(d)).containsOnlyKeys(water.id());
        assertThat(claim(NOW).document().id()).isEqualTo(d.id());
    }

    @Test
    void saveResultAfterTheRowWasResetIsIgnored() {
        CompanyId company = newCompany();
        Topic water = topic(company, "Agua");
        Document d = readyDocument(company, "Releído");
        claim(NOW);
        docs.replacePages(d.id(), List.of(new DocumentPage(d.id(), 1, "Texto nuevo", "lector", "2")));

        tagging.saveResult(d.id(), Set.of(water.id()), V1, NOW);

        assertThat(topicsOf(d)).isEmpty();
        assertThat(statusOf(d)).isEqualTo("SIN FILA");
    }

    @Test
    void replacingPagesRequeuesTagging() {
        CompanyId company = newCompany();
        Topic water = topic(company, "Agua");
        Document d = readyDocument(company, "Releído");
        UserId admin = newUser(company);
        tagDone(d, NOW, water.id());
        tagging.decide(CompanyScope.of(company), d.id(), water.id(), TopicState.REMOVED, admin, NOW);

        docs.replacePages(d.id(), List.of(new DocumentPage(d.id(), 1, "Texto nuevo", "lector", "2")));

        assertThat(statusOf(d)).isEqualTo("SIN FILA");
        assertThat(topicsOf(d).get(water.id()).state()).isEqualTo(TopicState.REMOVED);
        assertThat(claim(NOW).document().id()).isEqualTo(d.id());
    }

    @Test
    void retagDeletesTheStateAndKeepsDecisions() {
        CompanyId company = newCompany();
        CompanyId other = newCompany();
        Topic water = topic(company, "Agua");
        Document d = readyDocument(company, "Póliza");
        tagDone(d, NOW, water.id());

        tagging.retag(CompanyScope.of(other), d.id());
        assertThat(statusOf(d)).isEqualTo("DONE");
        tagging.retag(CompanyScope.of(company), d.id());

        assertThat(statusOf(d)).isEqualTo("SIN FILA");
        assertThat(topicsOf(d)).containsOnlyKeys(water.id());
    }

    @Test
    void topicsOfHidesDeletedTopicsAndOtherCompanies() {
        CompanyId company = newCompany();
        Topic water = topic(company, "Agua");
        Topic insurance = topic(company, "Seguros");
        Document d = readyDocument(company, "Póliza");
        tagDone(d, NOW, water.id(), insurance.id());

        topics.delete(company, insurance.id(), NOW);

        assertThat(topicsOf(d)).containsOnlyKeys(water.id());
        assertThat(tagging.topicsOf(CompanyScope.of(newCompany()), d.id())).isEmpty();
    }

    @Test
    void pendingCountCountsFailedAndRespectsVisibility() {
        CompanyId company = newCompany();
        Topic water = topic(company, "Agua");
        ProjectId open = newProject(company, "Abierto");
        ProjectId closed = newProject(company, "Cerrado");
        Document failed = readyDocument(company, "Fallido");
        Document done = readyDocument(company, "Hecho");
        claim(NOW);
        claim(NOW);
        tagging.markFailed(failed.id(), "roto");
        tagging.saveResult(done.id(), Set.of(water.id()), V1, NOW);
        readyDocument(company, "Sin proyecto");
        readyDocument(company, Optional.of(open), "En abierto");
        readyDocument(company, Optional.of(closed), "En cerrado");
        newDocument(company, newUser(company), "Sin leer", DocumentStatus.PROCESSING);

        // sin proyecto, en abierto y el fallido; no el cerrado, ni el hecho, ni el que no está leído
        assertThat(tagging.pendingCount(company, ProjectVisibility.of(Set.of(open)))).isEqualTo(3);
        assertThat(tagging.pendingCount(company, ProjectVisibility.of(Set.of()))).isEqualTo(2);
        assertThat(tagging.pendingCount(company, ProjectVisibility.all())).isEqualTo(4);
        assertThat(tagging.pendingCount(newCompany(), ProjectVisibility.all())).isZero();
    }

    @Test
    void listsActiveTopicsAndTaggingStateOfManyDocumentsRespectingVisibility() {
        CompanyId company = newCompany();
        Topic water = topic(company, "Agua");
        Topic insurance = topic(company, "Seguros");
        ProjectId closed = newProject(company, "Cerrado");
        Document tagged = readyDocument(company, "Etiquetado");
        Document hidden = readyDocument(company, Optional.of(closed), "Oculto");
        Document untagged = readyDocument(company, "Sin etiquetar");
        tagDone(tagged, NOW, water.id(), insurance.id());
        tagDone(hidden, NOW, water.id(), insurance.id());
        tagging.decide(CompanyScope.all(), tagged.id(), insurance.id(), TopicState.REMOVED, newUser(company), NOW);
        topics.delete(company, water.id(), NOW);
        Topic gas = topic(company, "Gas");
        tagging.decide(CompanyScope.all(), tagged.id(), gas.id(), TopicState.ACTIVE, newUser(company), NOW);
        List<DocumentId> ids = List.of(tagged.id(), hidden.id(), untagged.id());
        ProjectVisibility outsider = ProjectVisibility.of(Set.of());

        var active = tagging.activeTopicsOf(CompanyScope.of(company), outsider, ids);
        var states = tagging.taggingOf(CompanyScope.of(company), outsider, ids);

        assertThat(active).containsOnlyKeys(tagged.id());
        assertThat(active.get(tagged.id())).extracting(DocumentTopic::topicId).containsExactly(gas.id());
        assertThat(states).containsOnlyKeys(tagged.id());
        assertThat(states.get(tagged.id()).status().name()).isEqualTo("DONE");
        assertThat(tagging.activeTopicsOf(CompanyScope.of(company), ProjectVisibility.all(), ids))
                .containsOnlyKeys(tagged.id(), hidden.id());
        assertThat(tagging.taggingOf(CompanyScope.of(newCompany()), ProjectVisibility.all(), ids)).isEmpty();
        assertThat(tagging.activeTopicsOf(CompanyScope.of(newCompany()), ProjectVisibility.all(), ids)).isEmpty();
    }

    @Test
    void documentListFiltersByActiveTopicWithinVisibleProjects() {
        CompanyId company = newCompany();
        Topic water = topic(company, "Agua");
        ProjectId closed = newProject(company, "Cerrado");
        Document free = readyDocument(company, "Libre");
        Document hidden = readyDocument(company, Optional.of(closed), "Oculto");
        Document removed = readyDocument(company, "Quitado");
        readyDocument(company, "Sin tema");
        tagDone(free, NOW, water.id());
        tagDone(hidden, NOW, water.id());
        tagDone(removed, NOW, water.id());
        tagging.decide(CompanyScope.all(), removed.id(), water.id(), TopicState.REMOVED, newUser(company), NOW);
        CompanyScope scope = CompanyScope.of(company);
        Optional<TopicId> byWater = Optional.of(water.id());

        assertThat(docs.list(scope, ProjectVisibility.of(Set.of()), Optional.empty(), byWater, Optional.empty(), 0, 10))
                .extracting(e -> e.document().id()).containsExactly(free.id());
        assertThat(docs.list(scope, ProjectVisibility.all(), Optional.empty(), byWater, Optional.empty(), 0, 10))
                .extracting(e -> e.document().id()).containsExactlyInAnyOrder(free.id(), hidden.id());
        assertThat(docs.list(CompanyScope.of(newCompany()), ProjectVisibility.all(), Optional.empty(), byWater,
                Optional.empty(), 0, 10)).isEmpty();
    }

    @Test
    void pendingCountIsZeroWhenTheCompanyHasNoTopics() {
        CompanyId company = newCompany();
        readyDocument(company, "Sin lista");

        assertThat(tagging.pendingCount(company, ProjectVisibility.all())).isZero();
        assertThat(tagging.backlog(company)).isEqualTo(new TaggingBacklog(0, Optional.empty()));
    }

    @Test
    void backlogGivesCountAndOldestWaitingSince() {
        CompanyId company = newCompany();
        topic(company, "Agua");
        ProjectId closed = newProject(company, "Cerrado");
        Document read = readyDocument(company, "Leído por OCR");
        Document closedDoc = readyDocument(company, Optional.of(closed), "Cerrado");
        // subido en NOW, leído una hora después por el OCR; el otro, subido media hora después
        owner.sql("UPDATE document SET uploaded_at = :up, processing_started_at = :read WHERE id = :id")
                .param("up", Sql.ts(NOW)).param("read", Sql.ts(NOW.plus(Duration.ofHours(1))))
                .param("id", read.id().value()).update();
        owner.sql("UPDATE document SET uploaded_at = :up WHERE id = :id").param("up", Sql.ts(NOW.plus(Duration.ofMinutes(30))))
                .param("id", closedDoc.id().value()).update();
        setListChangedAt(company, NOW.minus(Duration.ofDays(1)));

        assertThat(tagging.backlog(company)).isEqualTo(new TaggingBacklog(2, Optional.of(NOW.plus(Duration.ofMinutes(30)))));

        setListChangedAt(company, NOW.plus(Duration.ofHours(2)));
        assertThat(tagging.backlog(company)).isEqualTo(new TaggingBacklog(2, Optional.of(NOW.plus(Duration.ofHours(2)))));

        tagDone(read, NOW);
        tagDone(closedDoc, NOW);
        assertThat(tagging.backlog(company)).isEqualTo(new TaggingBacklog(0, Optional.empty()));
    }

    private void setListChangedAt(CompanyId company, Instant at) {
        owner.sql("UPDATE company SET topic_list_changed_at = :at WHERE id = :id").param("at", Sql.ts(at))
                .param("id", company.value()).update();
    }

    @Test
    void rowLevelSecurityHidesOtherCompanyTagging() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Topic water = topic(a, "Agua");
        Topic seguros = topic(b, "Seguros");
        ProjectId closed = newProject(a, "Cerrado");
        Document inA = readyDocument(a, "De A");
        Document inClosed = readyDocument(a, Optional.of(closed), "Cerrado de A");
        Document inB = readyDocument(b, "De B");
        UserId admin = newUser(a);
        for (Document d : new Document[] {inA, inClosed, inB}) {
            claim(NOW);
            tagging.saveResult(d.id(), Set.of(d == inB ? seguros.id() : water.id()), V1, NOW);
        }
        tagging.decide(CompanyScope.of(a), inA.id(), water.id(), TopicState.REMOVED, admin, NOW);

        assertThat(rawCount("document_tagging", CompanyScope.of(a), ProjectVisibility.all())).isEqualTo(2);
        assertThat(rawCount("document_topic", CompanyScope.of(a), ProjectVisibility.all())).isEqualTo(2);
        assertThat(rawCount("document_tagging", CompanyScope.of(b), ProjectVisibility.all())).isEqualTo(1);
        assertThat(rawCount("document_topic", CompanyScope.all(), ProjectVisibility.all())).isEqualTo(3);
        // el proyecto cerrado del que no se es miembro queda fuera aunque la empresa sea la misma
        assertThat(rawCount("document_tagging", CompanyScope.of(a), ProjectVisibility.of(Set.of()))).isEqualTo(1);
        assertThat(rawCount("document_topic", CompanyScope.of(a), ProjectVisibility.of(Set.of()))).isEqualTo(1);
        assertThat(tagging.topicsOf(CompanyScope.of(b), inA.id())).isEmpty();
    }

    /** Sin filtro en el SQL: solo la seguridad por filas decide. */
    private long rawCount(String table, CompanyScope scope, ProjectVisibility visibility) {
        return tx.inScope(scope, visibility, jdbc -> jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single());
    }
}
