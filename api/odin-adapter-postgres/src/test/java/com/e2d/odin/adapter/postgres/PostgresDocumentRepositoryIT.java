package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresDocumentRepositoryIT extends AbstractPostgresIT {

    private static final Duration STALE = Duration.ofMinutes(15);

    @Test
    void savesFindsAndUpdatesWithinScope() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Document d = newDocument(a, newUser(a), "Manual", DocumentStatus.RECEIVED);
        assertThat(docs.findById(CompanyScope.of(a), d.id())).contains(d);
        assertThat(docs.findById(CompanyScope.all(), d.id())).contains(d);
        assertThat(docs.findById(CompanyScope.of(b), d.id())).isEmpty();
        Document failed = d.markFailed("roto");
        docs.save(failed);
        assertThat(docs.findById(CompanyScope.of(a), d.id())).contains(failed);
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
        assertThat(docs.list(CompanyScope.of(a), Optional.empty(), 0, 10)).containsExactly(alfa, zeta);
        assertThat(docs.list(CompanyScope.of(a), Optional.empty(), 1, 10)).containsExactly(zeta);
        assertThat(docs.list(CompanyScope.of(a), Optional.empty(), 0, 1)).containsExactly(alfa);
        assertThat(docs.list(CompanyScope.all(), Optional.empty(), 0, 10)).containsExactly(otra, alfa, zeta);
        assertThat(docs.list(CompanyScope.of(b), Optional.of(new ProjectId(UUID.randomUUID())), 0, 10)).isEmpty();
    }

    @Test
    void listFiltersByProject() {
        CompanyId a = newCompany();
        UserId ua = newUser(a);
        ProjectId p = new ProjectId(UUID.randomUUID());
        orgs.saveProject(new Project(p, a, "P"));
        Document d = newDocument(a, ua, "Con proyecto", DocumentStatus.READY);
        Document inProject = new Document(d.id(), a, Optional.of(p), d.title(), d.filename(), d.format(), d.sha256(),
                d.status(), d.failureReason(), d.paginated(), ua, d.uploadedAt(), d.processingStartedAt(), 0);
        docs.save(inProject);
        newDocument(a, ua, "Sin proyecto", DocumentStatus.READY);
        assertThat(docs.list(CompanyScope.of(a), Optional.of(p), 0, 10)).containsExactly(inProject);
    }

    @Test
    void pagesAreReplacedAndFoundWithinScope() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Document d = newDocument(a, newUser(a), "Manual", DocumentStatus.READY);
        docs.replacePages(d.id(), List.of(page(d.id(), 1, "viejo")));
        docs.replacePages(d.id(), List.of(page(d.id(), 1, "uno"), page(d.id(), 2, "dos")));
        assertThat(docs.findPage(CompanyScope.of(a), d.id(), 2)).contains(page(d.id(), 2, "dos"));
        assertThat(docs.findPage(CompanyScope.of(a), d.id(), 3)).isEmpty();
        assertThat(docs.findPage(CompanyScope.of(b), d.id(), 1)).isEmpty();
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
        assertThat(docs.findById(CompanyScope.all(), first.id())).contains(claimed);
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
    void claimIgnoresFailedAndPendingReader() {
        CompanyId a = newCompany();
        UserId ua = newUser(a);
        newDocument(a, ua, "Fallido", DocumentStatus.FAILED);
        newDocument(a, ua, "Pendiente", DocumentStatus.PENDING_READER);
        assertThat(docs.claimNextForProcessing(NOW.plus(Duration.ofDays(1)), STALE)).isEmpty();
    }

    private static Document copyIn(Document d, CompanyId company, UserId uploader) {
        return new Document(new DocumentId(UUID.randomUUID()), company, Optional.empty(), d.title(), d.filename(),
                d.format(), d.sha256(), d.status(), Optional.empty(), true, uploader, d.uploadedAt(), Optional.empty(),
                0);
    }

    private static DocumentPage page(DocumentId id, int number, String text) {
        return new DocumentPage(id, number, text, "lector", "1.0");
    }
}
