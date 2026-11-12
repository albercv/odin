package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

class PostgresDocumentRepositoryIT extends AbstractPostgresIT {

    private static final Duration STALE = Duration.ofMinutes(15);

    @Test
    void savesFindsAndUpdatesWithinScope() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Document d = newDocument(a, newUser(a), "Manual", DocumentStatus.RECEIVED);
        assertThat(docs.findById(CompanyScope.of(a), ProjectVisibility.all(), d.id())).contains(d);
        assertThat(docs.findById(CompanyScope.all(), ProjectVisibility.all(), d.id())).contains(d);
        assertThat(docs.findById(CompanyScope.of(b), ProjectVisibility.all(), d.id())).isEmpty();
        Document failed = d.markFailed("roto");
        docs.save(failed);
        assertThat(docs.findById(CompanyScope.of(a), ProjectVisibility.all(), d.id())).contains(failed);
    }

    @Test
    void findBySha256IsPerCompany() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Document d = newDocument(a, newUser(a), "Manual", DocumentStatus.READY);
        assertThat(docs.findBySha256(a, d.sha256())).contains(d);
        assertThat(docs.findBySha256(b, d.sha256())).isEmpty();
    }

    @Test
    void sameHashInTheSameCompanyIsRejectedButAnotherCompanyMayRepeatIt() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Document d = newDocument(a, newUser(a), "Manual", DocumentStatus.READY);
        docs.save(copyIn(d, b, newUser(b)));
        assertThatThrownBy(() -> docs.save(copyIn(d, a, d.uploadedBy()))).isInstanceOf(ConflictException.class);
    }

    @Test
    void listOrdersByTitleThenIdAndFiltersByScopeProjectAndPage() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        UserId ua = newUser(a);
        Document zeta = newDocument(a, ua, "Zeta", DocumentStatus.READY);
        Document alfa = newDocument(a, ua, "Alfa", DocumentStatus.READY);
        Document otra = newDocument(b, newUser(b), "Aaa", DocumentStatus.READY);
        assertThat(listed(CompanyScope.of(a), ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 10)).containsExactly(alfa, zeta);
        assertThat(listed(CompanyScope.of(a), ProjectVisibility.all(), Optional.empty(), Optional.empty(), 1, 10)).containsExactly(zeta);
        assertThat(listed(CompanyScope.of(a), ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 1)).containsExactly(alfa);
        assertThat(listed(CompanyScope.all(), ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 10)).containsExactly(otra, alfa, zeta);
        assertThat(listed(CompanyScope.of(b), ProjectVisibility.all(), Optional.of(new ProjectId(UUID.randomUUID())), Optional.empty(), 0, 10)).isEmpty();
    }

    @Test
    void listFiltersByStatus() {
        CompanyId a = newCompany();
        UserId ua = newUser(a);
        Document ready = newDocument(a, ua, "Listo", DocumentStatus.READY);
        newDocument(a, ua, "Fallido", DocumentStatus.FAILED);
        newDocument(a, ua, "Pendiente", DocumentStatus.PENDING_READER);
        assertThat(listed(CompanyScope.of(a), ProjectVisibility.all(), Optional.empty(), Optional.of(DocumentStatus.READY), 0, 10))
                .containsExactly(ready);
        assertThat(listed(CompanyScope.of(a), ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 10)).hasSize(3);
    }

    @Test
    void listFiltersByProject() {
        CompanyId a = newCompany();
        UserId ua = newUser(a);
        ProjectId p = new ProjectId(UUID.randomUUID());
        orgs.saveProject(new Project(p, a, "P", true));
        Document d = newDocument(a, ua, "Con proyecto", DocumentStatus.READY);
        Document inProject = new Document(d.id(), a, Optional.of(p), d.title(), d.filename(), d.format(), d.sha256(),
                d.status(), d.failureReason(), d.paginated(), ua, d.uploadedAt(), d.processingStartedAt(), 0, Optional.empty(), Optional.empty());
        docs.save(inProject);
        newDocument(a, ua, "Sin proyecto", DocumentStatus.READY);
        assertThat(listed(CompanyScope.of(a), ProjectVisibility.all(), Optional.of(p), Optional.empty(), 0, 10)).containsExactly(inProject);
    }

    @Test
    void pagesAreReplacedAndFoundWithinScope() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Document d = newDocument(a, newUser(a), "Manual", DocumentStatus.READY);
        docs.replacePages(d.id(), List.of(page(d.id(), 1, "viejo")));
        docs.replacePages(d.id(), List.of(page(d.id(), 1, "uno"), page(d.id(), 2, "dos")));
        assertThat(docs.findPage(CompanyScope.of(a), ProjectVisibility.all(), d.id(), 2)).contains(page(d.id(), 2, "dos"));
        assertThat(docs.findPage(CompanyScope.of(a), ProjectVisibility.all(), d.id(), 3)).isEmpty();
        assertThat(docs.findPage(CompanyScope.of(b), ProjectVisibility.all(), d.id(), 1)).isEmpty();
    }

    @Test
    void claimTakesReceivedOldestFirstAndCountsTheAttempt() {
        CompanyId a = newCompany();
        UserId ua = newUser(a);
        Document first = newDocument(a, ua, "Uno", DocumentStatus.RECEIVED);
        Document second = newDocument(newCompany(), newUser(a), "Dos", DocumentStatus.RECEIVED);
        newDocument(a, ua, "Lista", DocumentStatus.READY);
        Document claimed = docs.claimNextForProcessing(NOW, STALE).orElseThrow();
        assertThat(claimed).isEqualTo(first.startProcessing(NOW));
        assertThat(docs.findById(CompanyScope.all(), ProjectVisibility.all(), first.id())).contains(claimed);
        assertThat(docs.claimNextForProcessing(NOW, STALE).orElseThrow().id()).isEqualTo(second.id());
        assertThat(docs.claimNextForProcessing(NOW, STALE)).isEmpty();
    }

    @Test
    void claimTakesProcessingOnlyWhenStale() {
        CompanyId a = newCompany();
        Document d = newDocument(a, newUser(a), "Colgado", DocumentStatus.RECEIVED);
        docs.claimNextForProcessing(NOW, STALE);
        assertThat(docs.claimNextForProcessing(NOW.plus(STALE).minusSeconds(1), STALE)).isEmpty();
        Document again = docs.claimNextForProcessing(NOW.plus(STALE), STALE).orElseThrow();
        assertThat(again.id()).isEqualTo(d.id());
        assertThat(again.attemptCount()).isEqualTo(2);
    }

    @Test
    void localClaimNeverTakesReaderStates() {
        CompanyId a = newCompany();
        UserId ua = newUser(a);
        newDocument(a, ua, "Fallido", DocumentStatus.FAILED);
        newDocument(a, ua, "Pendiente", DocumentStatus.PENDING_READER);
        newDocument(a, ua, "Leyendo", DocumentStatus.READER_PROCESSING);
        assertThat(docs.claimNextForProcessing(NOW.plus(Duration.ofDays(1)), STALE)).isEmpty();
    }

    @Test
    void claimNextForReaderTakesPendingReaderOnly() {
        CompanyId a = newCompany();
        UserId ua = newUser(a);
        newDocument(a, ua, "Recibido", DocumentStatus.RECEIVED);
        newDocument(a, ua, "Procesando", DocumentStatus.PROCESSING);
        newDocument(a, ua, "Listo", DocumentStatus.READY);
        newDocument(a, ua, "Fallido", DocumentStatus.FAILED);
        Document waiting = newDocument(a, ua, "Escaneo", DocumentStatus.PENDING_READER);
        Document claimed = docs.claimNextForReader(NOW, STALE).orElseThrow();
        assertThat(claimed).isEqualTo(waiting.startReading(NOW));
        assertThat(reread(waiting)).isEqualTo(claimed);
        assertThat(docs.claimNextForReader(NOW, STALE)).isEmpty();
    }

    @Test
    void claimNextForReaderSkipsLockedRows() throws Exception {
        CompanyId a = newCompany();
        UserId ua = newUser(a);
        Document first = newDocument(a, ua, "Uno", DocumentStatus.PENDING_READER);
        Document second = newDocument(a, ua, "Dos", DocumentStatus.PENDING_READER);
        try (java.sql.Connection c = Db.owner().getConnection()) {
            c.setAutoCommit(false);
            try (java.sql.PreparedStatement lock = c.prepareStatement("SELECT id FROM document WHERE id = ? FOR UPDATE")) {
                lock.setObject(1, first.id().value());
                lock.executeQuery();
            }
            assertThat(docs.claimNextForReader(NOW, STALE).orElseThrow().id()).isEqualTo(second.id());
            assertThat(docs.claimNextForReader(NOW, STALE)).isEmpty();
            c.rollback();
        }
    }

    @Test
    void claimNextForReaderRetakesStaleReaderProcessing() {
        CompanyId a = newCompany();
        Document d = newDocument(a, newUser(a), "Colgado", DocumentStatus.PENDING_READER);
        docs.claimNextForReader(NOW, STALE);
        assertThat(docs.claimNextForReader(NOW.plus(STALE).minusSeconds(1), STALE)).isEmpty();
        Document again = docs.claimNextForReader(NOW.plus(STALE), STALE).orElseThrow();
        assertThat(again.id()).isEqualTo(d.id());
        assertThat(again.status()).isEqualTo(DocumentStatus.READER_PROCESSING);
        assertThat(again.attemptCount()).isEqualTo(2);
    }

    @Test
    void storesPagesWithoutText() {
        CompanyId a = newCompany();
        Document d = newDocument(a, newUser(a), "Mixto", DocumentStatus.PROCESSING);
        Document ready = d.markReady(true, List.of(1, 3));
        docs.save(ready);
        assertThat(reread(d).pagesWithoutText()).containsExactly(1, 3);
        assertThat(entry(a, ProjectVisibility.all(), d).document()).isEqualTo(ready);
        docs.save(ready.markReady(true, List.of()));
        assertThat(reread(d).pagesWithoutText()).isEmpty();
    }

    private List<Document> listed(CompanyScope scope, ProjectVisibility visibility, Optional<ProjectId> project,
            Optional<DocumentStatus> status, int offset, int limit) {
        return docs.list(scope, visibility, project, status, offset, limit).stream().map(DocumentEntry::document)
                .toList();
    }

    private static final Instant MARKED_AT = Instant.parse("2026-11-12T09:00:00Z");

    private Document ready(CompanyId company, String title) {
        return newDocument(company, newUser(company), title, DocumentStatus.READY);
    }

    private void mark(Document old, Document replacement) {
        docs.markSuperseded(old.companyId(), old.id(), replacement.id(), MARKED_AT);
    }

    private Document reread(Document d) {
        return docs.findById(CompanyScope.all(), ProjectVisibility.all(), d.id()).orElseThrow();
    }

    private DocumentEntry entry(CompanyId company, ProjectVisibility visibility, Document d) {
        return docs.findEntry(CompanyScope.of(company), visibility, d.id()).orElseThrow();
    }

    @Test
    void marksChangesAndClearsTheMark() {
        CompanyId a = newCompany();
        Document old = ready(a, "Póliza 2022");
        Document b = ready(a, "Póliza 2023");
        Document c = ready(a, "Póliza 2024");
        mark(old, b);
        assertThat(reread(old).supersededBy()).contains(b.id());
        assertThat(reread(old).supersededAt()).contains(MARKED_AT);
        docs.markSuperseded(a, old.id(), c.id(), MARKED_AT.plusSeconds(60));
        assertThat(reread(old).supersededBy()).contains(c.id());
        assertThat(reread(old).supersededAt()).contains(MARKED_AT.plusSeconds(60));
        docs.clearSuperseded(a, old.id());
        assertThat(reread(old).superseded()).isFalse();
        assertThat(reread(old).supersededAt()).isEmpty();
        docs.clearSuperseded(a, old.id());
        assertThat(reread(old).superseded()).isFalse();
    }

    @Test
    void markOfAnotherCompanyIsRejectedAndLeavesNoMark() {
        CompanyId a = newCompany();
        Document old = ready(a, "Póliza 2022");
        Document b = ready(a, "Póliza 2023");
        assertThatThrownBy(() -> docs.markSuperseded(newCompany(), old.id(), b.id(), MARKED_AT))
                .isInstanceOf(ConflictException.class);
        assertThat(reread(old).superseded()).isFalse();
    }

    @Test
    void replacementOfAnotherCompanyIsRejectedByTheRepositoryAndByTheTable() {
        CompanyId a = newCompany();
        Document x = ready(a, "A");
        Document foreign = ready(newCompany(), "Ajeno");
        assertThatThrownBy(() -> mark(x, foreign)).isInstanceOf(ConflictException.class);
        assertThat(reread(x).superseded()).isFalse();
        assertThatThrownBy(() -> forceMark(x, foreign)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void replacementMustBeInTheSameProjectOrWithoutProject() {
        CompanyId a = newCompany();
        ProjectId p1 = newProject(a, "Uno");
        ProjectId p2 = newProject(a, "Dos");
        Document free = ready(a, "Libre");
        Document in1 = inProject(ready(a, "En uno"), p1);
        Document other1 = inProject(ready(a, "Otro en uno"), p1);
        Document in2 = inProject(ready(a, "En dos"), p2);
        assertThatThrownBy(() -> mark(free, in1)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> mark(in1, in2)).isInstanceOf(ConflictException.class);
        assertThat(reread(free).superseded()).isFalse();
        assertThat(reread(in1).superseded()).isFalse();
        mark(in1, other1);
        mark(in1, free);
        assertThat(reread(in1).supersededBy()).contains(free.id());
    }

    @Test
    void lockHoldsTheTwoDocumentsUntilTheWorkEnds() throws Exception {
        CompanyId a = newCompany();
        Document x = ready(a, "A");
        Document y = ready(a, "B");
        java.util.concurrent.CountDownLatch inside = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> docs.withDocumentsLocked(a, x.id(), y.id(), () -> {
                inside.countDown();
                await(release);
                return "primero";
            }));
            assertThat(inside.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> docs.withDocumentsLocked(a, y.id(), x.id(), () -> "segundo"));
            assertThatThrownBy(() -> second.get(500, java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            assertThat(first.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("primero");
            assertThat(second.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("segundo");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static void await(java.util.concurrent.CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void saveOfAStaleCopyKeepsTheMark() {
        CompanyId a = newCompany();
        Document old = ready(a, "Póliza 2022");
        Document replacement = ready(a, "Póliza 2023");
        Document stale = reread(old);
        mark(old, replacement);
        docs.save(stale.startProcessing(NOW));
        assertThat(reread(old).supersededBy()).contains(replacement.id());
        assertThat(reread(old).supersededAt()).contains(MARKED_AT);
        docs.clearSuperseded(a, old.id());
        mark(old, replacement);
        docs.save(stale);
        assertThat(reread(old).supersededBy()).contains(replacement.id());
    }

    @Test
    void entryResolvesDirectAndEndOfChain() {
        CompanyId a = newCompany();
        Document x = ready(a, "A");
        Document y = ready(a, "B");
        Document z = ready(a, "C");
        mark(x, y);
        mark(y, z);
        DocumentEntry entry = entry(a, ProjectVisibility.all(), x);
        assertThat(entry.document().supersededBy()).contains(y.id());
        assertThat(entry.supersededBy()).contains(DocumentRef.of(y));
        assertThat(entry.replacement()).contains(DocumentRef.of(z));
        DocumentEntry middle = entry(a, ProjectVisibility.all(), y);
        assertThat(middle.supersededBy()).contains(DocumentRef.of(z));
        assertThat(middle.replacement()).contains(DocumentRef.of(z));
        DocumentEntry current = entry(a, ProjectVisibility.all(), z);
        assertThat(current.supersededBy()).isEmpty();
        assertThat(current.replacement()).isEmpty();
        assertThat(current.document().superseded()).isFalse();
    }

    @Test
    void brokenChainGivesNoReplacement() {
        CompanyId a = newCompany();
        ProjectId closed = newProject(a, "Cerrado");
        Document x = ready(a, "A");
        Document y = inProject(ready(a, "B"), closed);
        Document z = ready(a, "C");
        forceMark(x, y);
        forceMark(y, z);
        ProjectVisibility withoutClosed = ProjectVisibility.of(Set.of());
        DocumentEntry entry = entry(a, withoutClosed, x);
        assertThat(entry.document().superseded()).isTrue();
        assertThat(entry.supersededBy()).isEmpty();
        assertThat(entry.replacement()).isEmpty();
        DocumentEntry seen = entry(a, ProjectVisibility.of(Set.of(closed)), x);
        assertThat(seen.supersededBy()).contains(DocumentRef.of(y));
        assertThat(seen.replacement()).contains(DocumentRef.of(z));
    }

    @Test
    void circularDataDoesNotHangReads() {
        CompanyId a = newCompany();
        Document x = ready(a, "A");
        Document y = ready(a, "B");
        mark(x, y);
        mark(y, x);
        DocumentEntry entry = entry(a, ProjectVisibility.all(), x);
        assertThat(entry.supersededBy()).contains(DocumentRef.of(y));
        assertThat(entry.replacement()).isEmpty();
        assertThat(docs.list(CompanyScope.of(a), ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 10))
                .allSatisfy(e -> assertThat(e.replacement()).isEmpty());
    }

    @Test
    void entryOfAnotherCompanyIsEmptyAndTheirReplacementIsNotResolved() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Document x = ready(a, "A");
        Document foreign = ready(b, "Ajeno");
        forceMarkAcrossCompanies(x, foreign);
        assertThat(docs.findEntry(CompanyScope.of(b), ProjectVisibility.all(), x.id())).isEmpty();
        DocumentEntry entry = entry(a, ProjectVisibility.all(), x);
        assertThat(entry.supersededBy()).isEmpty();
        assertThat(entry.replacement()).isEmpty();
    }

    @Test
    void groupScopeDoesNotFollowAMarkIntoAnotherCompany() {
        CompanyId a = newCompany();
        Document x = ready(a, "A");
        CompanyId b = newCompany();
        Document foreign = ready(b, "Ajeno");
        Document foreignNext = ready(b, "Ajeno siguiente");
        forceMarkAcrossCompanies(x, foreign);
        forceMark(foreign, foreignNext);
        DocumentEntry entry = docs.findEntry(CompanyScope.all(), ProjectVisibility.all(), x.id()).orElseThrow();
        assertThat(entry.supersededBy()).isEmpty();
        assertThat(entry.replacement()).isEmpty();
        assertThat(docs.list(CompanyScope.all(), ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 10)
                .stream().filter(e -> e.document().id().equals(x.id()))).allSatisfy(e -> {
                    assertThat(e.supersededBy()).isEmpty();
                    assertThat(e.replacement()).isEmpty();
                });
    }

    @Test
    void listCarriesEntries() {
        CompanyId a = newCompany();
        Document x = ready(a, "A");
        Document y = ready(a, "B");
        Document z = ready(a, "C");
        mark(x, y);
        mark(y, z);
        List<DocumentEntry> entries = docs.list(CompanyScope.of(a), ProjectVisibility.all(), Optional.empty(),
                Optional.empty(), 0, 10);
        assertThat(entries).extracting(e -> e.document().id()).containsExactly(x.id(), y.id(), z.id());
        assertThat(entries).extracting(e -> e.supersededBy().map(DocumentRef::id))
                .containsExactly(Optional.of(y.id()), Optional.of(z.id()), Optional.empty());
        assertThat(entries).extracting(e -> e.replacement().map(DocumentRef::id))
                .containsExactly(Optional.of(z.id()), Optional.of(z.id()), Optional.empty());
    }

    @Test
    void tableRejectsSelfMarkAndHalfMarks() {
        CompanyId a = newCompany();
        Document x = ready(a, "A");
        Document y = ready(a, "B");
        JdbcClient jdbc = JdbcClient.create(Db.owner());
        assertThatThrownBy(() -> jdbc.sql("UPDATE document SET superseded_by = id, superseded_at = now() "
                + "WHERE id = :id").param("id", x.id().value()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("UPDATE document SET superseded_by = :y WHERE id = :id")
                .param("y", y.id().value()).param("id", x.id().value()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("UPDATE document SET superseded_at = now() WHERE id = :id")
                .param("id", x.id().value()).update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Document copyIn(Document d, CompanyId company, UserId uploader) {
        return new Document(new DocumentId(UUID.randomUUID()), company, Optional.empty(), d.title(), d.filename(),
                d.format(), d.sha256(), d.status(), Optional.empty(), true, uploader, d.uploadedAt(), Optional.empty(),
                0, Optional.empty(), Optional.empty());
    }

    private static DocumentPage page(DocumentId id, int number, String text) {
        return new DocumentPage(id, number, text, "lector", "1.0");
    }

    @Test
    void filtersByVisibleProjects() {
        CompanyId a = newCompany();
        UserId ua = newUser(a);
        ProjectId visible = newProject(a, "Visible");
        ProjectId hidden = newProject(a, "Oculto");
        Document free = newDocument(a, ua, "Libre", DocumentStatus.READY);
        Document inVisible = inProject(newDocument(a, ua, "En visible", DocumentStatus.READY), visible);
        Document inHidden = inProject(newDocument(a, ua, "En oculto", DocumentStatus.READY), hidden);
        for (Document d : List.of(free, inVisible, inHidden)) {
            docs.replacePages(d.id(), List.of(page(d.id(), 1, "uno")));
        }
        ProjectVisibility some = ProjectVisibility.of(Set.of(visible));
        CompanyScope scope = CompanyScope.of(a);

        assertThat(listed(scope, some, Optional.empty(), Optional.empty(), 0, 10))
                .containsExactly(inVisible, free);
        assertThat(listed(scope, ProjectVisibility.of(Set.of()), Optional.empty(), Optional.empty(), 0, 10))
                .containsExactly(free);
        assertThat(listed(scope, ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 10))
                .containsExactly(inHidden, inVisible, free);
        assertThat(listed(scope, some, Optional.of(hidden), Optional.empty(), 0, 10)).isEmpty();
        assertThat(docs.findById(scope, some, inHidden.id())).isEmpty();
        assertThat(docs.findById(scope, some, inVisible.id())).contains(inVisible);
        assertThat(docs.findById(scope, some, free.id())).contains(free);
        assertThat(docs.findPage(scope, some, inHidden.id(), 1)).isEmpty();
        assertThat(docs.findPage(scope, some, inVisible.id(), 1)).isPresent();
        assertThat(docs.findPage(scope, some, free.id(), 1)).isPresent();
        assertThat(docs.findPage(scope, ProjectVisibility.all(), inHidden.id(), 1)).isPresent();
    }

    private Document inProject(Document d, ProjectId project) {
        Document moved = new Document(d.id(), d.companyId(), Optional.of(project), d.title(), d.filename(), d.format(),
                d.sha256(), d.status(), d.failureReason(), d.paginated(), d.uploadedBy(), d.uploadedAt(),
                d.processingStartedAt(), 0, Optional.empty(), Optional.empty());
        docs.save(moved);
        return moved;
    }
}
