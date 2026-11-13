package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.casefile.CaseFileAction;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class PostgresCaseFileRepositoryIT extends AbstractPostgresIT {

    private final PostgresCaseFileRepository files = new PostgresCaseFileRepository(tx);
    private final JdbcClient owner = JdbcClient.create(Db.owner());
    private CompanyId company;
    private UserId user;
    private ProjectId projectA;
    private ProjectId projectB;

    @BeforeEach
    void seed() {
        company = newCompany();
        user = newUser(company);
        projectA = newProject(company, "A");
        projectB = newProject(company, "B");
    }

    private Document document(Optional<ProjectId> project, String sha, boolean caseDocument) {
        Document d = new Document(new DocumentId(UUID.randomUUID()), company, project, "Doc", "f.pdf",
                DocumentFormat.PDF, sha, DocumentStatus.READY, Optional.empty(), true, user, NOW, Optional.empty(), 0,
                Optional.empty(), Optional.empty()).withCaseDocument(caseDocument);
        docs.save(d);
        docs.markCaseDocuments(company, Set.of(d.id()), caseDocument);
        return d;
    }

    private Document document(Optional<ProjectId> project, boolean caseDocument) {
        return document(project, UUID.randomUUID().toString(), caseDocument);
    }

    private CaseFileEntry entry(Document theCase, Document member) {
        return new CaseFileEntry(company, theCase.id(), member.id(), user, NOW);
    }

    private void insertDirect(Document theCase, Document member) {
        owner.sql("INSERT INTO case_file_entry VALUES (:c, :case, :doc, :by, now())").param("c", company.value())
                .param("case", theCase.id().value()).param("doc", member.id().value()).param("by", user.value())
                .update();
    }

    @Test
    void addIsIdempotent() {
        Document theCase = document(Optional.of(projectA), true);
        Document member = document(Optional.of(projectA), false);

        assertThat(files.add(entry(theCase, member))).isTrue();
        assertThat(files.add(entry(theCase, member))).isFalse();
        assertThat(files.countEntries(company, theCase.id())).isEqualTo(1);
        assertThat(files.entries(CompanyScope.of(company), ProjectVisibility.all(), theCase.id()))
                .containsExactly(entry(theCase, member));
        assertThat(files.remove(company, theCase.id(), member.id())).isTrue();
        assertThat(files.remove(company, theCase.id(), member.id())).isFalse();
    }

    private CaseFileEvent event(Document theCase, Document member, CaseFileAction action) {
        return new CaseFileEvent(UUID.randomUUID(), company, theCase.id(), member.id(), action, user, NOW);
    }

    @Test
    void addAllWritesEntriesAndEventsTogetherAndSkipsExisting() {
        Document theCase = document(Optional.of(projectA), true);
        Document one = document(Optional.of(projectA), false);
        Document two = document(Optional.empty(), false);
        files.add(entry(theCase, one));

        int added = files.addAll(java.util.List.of(entry(theCase, one), entry(theCase, two)),
                e -> event(theCase, docs.findById(CompanyScope.of(company), ProjectVisibility.all(),
                        e.documentId()).orElseThrow(), CaseFileAction.ADDED));

        assertThat(added).isEqualTo(1);
        assertThat(files.events(CompanyScope.of(company), ProjectVisibility.all(), theCase.id()))
                .extracting(CaseFileEvent::documentId).containsExactly(two.id());
    }

    @Test
    void addAllLeavesNothingWhenOnePairFails() {
        Document theCase = document(Optional.of(projectA), true);
        Document good = document(Optional.of(projectA), false);
        Document foreign = document(Optional.of(projectB), false);

        assertThatThrownBy(() -> files.addAll(java.util.List.of(entry(theCase, good), entry(theCase, foreign)),
                e -> event(theCase, good, CaseFileAction.ADDED))).isInstanceOf(RuntimeException.class);

        assertThat(files.countEntries(company, theCase.id())).isZero();
        assertThat(files.events(CompanyScope.of(company), ProjectVisibility.all(), theCase.id())).isEmpty();
    }

    @Test
    void removeAndRecordIsOneStepAndSilentWhenNothingToRemove() {
        Document theCase = document(Optional.of(projectA), true);
        Document member = document(Optional.of(projectA), false);
        files.add(entry(theCase, member));

        assertThat(files.removeAndRecord(event(theCase, member, CaseFileAction.REMOVED))).isTrue();
        assertThat(files.removeAndRecord(event(theCase, member, CaseFileAction.REMOVED))).isFalse();

        assertThat(files.events(CompanyScope.of(company), ProjectVisibility.all(), theCase.id())).hasSize(1);
    }

    @Test
    void removeHiddenEntriesDeletesRelationsOfWithdrawnDocumentsAndRecordsRemoved() {
        Document theCase = document(Optional.of(projectA), true);
        Document member = document(Optional.of(projectA), false);
        files.add(entry(theCase, member));
        owner.sql("UPDATE document SET withdrawn_at = now(), withdrawn_by = :u WHERE id = :id")
                .param("u", user.value()).param("id", member.id().value()).update();
        assertThat(files.countEntries(company, theCase.id())).isEqualTo(1);
        assertThat(files.entries(CompanyScope.of(company), ProjectVisibility.all(), theCase.id())).isEmpty();

        int removed = files.removeHiddenEntries(company, theCase.id(), user, NOW);

        assertThat(removed).isEqualTo(1);
        assertThat(files.countEntries(company, theCase.id())).isZero();
        assertThat(owner.sql("SELECT count(*) FROM case_file_event WHERE case_document_id = :c AND action = 'REMOVED'"
                + " AND actor_id = :u").param("c", theCase.id().value()).param("u", user.value())
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void pageCountsAreOneAggregateForTheVisibleDocuments() {
        Document two = document(Optional.of(projectA), false);
        Document none = document(Optional.of(projectA), false);
        docs.replacePages(two.id(), java.util.List.of(new com.e2d.odin.domain.document.DocumentPage(two.id(), 1, "a",
                "stub", "1"), new com.e2d.odin.domain.document.DocumentPage(two.id(), 2, "b", "stub", "1")));

        var counts = files.pageCounts(CompanyScope.of(company), ProjectVisibility.all(),
                java.util.List.of(two.id(), none.id()));

        assertThat(counts).containsEntry(two.id(), 2).doesNotContainKey(none.id());
    }

    @Test
    void triggerRejectsDocumentFromOtherProject() {
        Document theCase = document(Optional.of(projectA), true);
        Document foreign = document(Optional.of(projectB), false);

        assertThatThrownBy(() -> insertDirect(theCase, foreign)).hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    void triggerRejectsNonCase() {
        Document notCase = document(Optional.of(projectA), false);
        Document member = document(Optional.of(projectA), false);

        assertThatThrownBy(() -> insertDirect(notCase, member)).hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    void triggerRejectsWithdrawn() {
        Document theCase = document(Optional.of(projectA), true);
        Document member = document(Optional.of(projectA), false);
        owner.sql("UPDATE document SET withdrawn_at = now(), withdrawn_by = :u WHERE id = :id")
                .param("u", user.value()).param("id", member.id().value()).update();

        assertThatThrownBy(() -> insertDirect(theCase, member)).hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    void generalDocumentAllowed() {
        Document theCase = document(Optional.of(projectA), true);
        Document general = document(Optional.empty(), false);

        assertThat(files.add(entry(theCase, general))).isTrue();
    }

    @Test
    void sameFileAllowedInTwoProjects() {
        String sha = UUID.randomUUID().toString();
        document(Optional.of(projectA), sha, false);
        document(Optional.of(projectB), sha, false);

        assertThatThrownBy(() -> document(Optional.of(projectA), sha, false)).isInstanceOf(ConflictException.class);
        document(Optional.empty(), sha, false);
        assertThatThrownBy(() -> document(Optional.empty(), sha, false)).isInstanceOf(ConflictException.class);
    }

    @Test
    void findBySha256ReturnsTheOldestAndInProjectLooksOnlyThereAndInGeneral() {
        String sha = UUID.randomUUID().toString();
        Document inA = document(Optional.of(projectA), sha, false);
        Document inB = document(Optional.of(projectB), sha, false);
        Document general = document(Optional.empty(), sha, false);

        assertThat(docs.findBySha256(company, sha).orElseThrow().id()).isEqualTo(inA.id());
        assertThat(docs.findBySha256InProject(company, Optional.of(projectB), sha).orElseThrow().id())
                .isEqualTo(inB.id());
        assertThat(docs.findBySha256InProject(company, Optional.empty(), sha).orElseThrow().id())
                .isEqualTo(general.id());
        assertThat(docs.findBySha256InProject(company, Optional.of(newProject(company, "C")), sha).orElseThrow().id())
                .isEqualTo(general.id());
    }

    @Test
    void caseCannotContainItself() {
        Document theCase = document(Optional.of(projectA), true);

        assertThatThrownBy(() -> insertDirect(theCase, theCase)).hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    void projectChangeGuardRejectsBreakingMove() {
        Document theCase = document(Optional.of(projectA), true);
        Document member = document(Optional.of(projectA), false);
        files.add(entry(theCase, member));

        assertThatThrownBy(() -> moveTo(member, projectB)).hasRootCauseInstanceOf(SQLException.class);
        assertThatThrownBy(() -> moveTo(theCase, projectB)).hasRootCauseInstanceOf(SQLException.class);
        moveTo(member, null);
        assertThat(files.countEntries(company, theCase.id())).isEqualTo(1);
    }

    private void moveTo(Document document, ProjectId project) {
        owner.sql("UPDATE document SET project_id = :p WHERE id = :id")
                .param("p", project == null ? null : project.value()).param("id", document.id().value()).update();
    }

    @Test
    void eventsAreAppendOnlyOrder() {
        Document theCase = document(Optional.of(projectA), true);
        Document member = document(Optional.of(projectA), false);
        files.record(event(theCase, member, CaseFileAction.REMOVED, NOW.plusSeconds(10)));
        files.record(event(theCase, member, CaseFileAction.ADDED, NOW));

        assertThat(files.events(CompanyScope.of(company), ProjectVisibility.all(), theCase.id()))
                .extracting(CaseFileEvent::action).containsExactly(CaseFileAction.ADDED, CaseFileAction.REMOVED);
    }

    @Test
    void entriesHideRelationsWithInvisibleDocumentsAndWithdrawn() {
        Document theCase = document(Optional.of(projectA), true);
        Document member = document(Optional.empty(), false);
        files.add(entry(theCase, member));
        CompanyScope scope = CompanyScope.of(company);

        assertThat(files.entries(scope, ProjectVisibility.of(Set.of(projectB)), theCase.id())).isEmpty();
        owner.sql("UPDATE document SET withdrawn_at = now(), withdrawn_by = :u WHERE id = :id")
                .param("u", user.value()).param("id", member.id().value()).update();
        assertThat(files.entries(scope, ProjectVisibility.all(), theCase.id())).isEmpty();
        assertThat(files.countEntries(company, theCase.id())).isEqualTo(1);
    }

    private CaseFileEvent event(Document theCase, Document member, CaseFileAction action, Instant at) {
        return new CaseFileEvent(UUID.randomUUID(), company, theCase.id(), member.id(), action, user, at);
    }
}
