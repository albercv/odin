package com.e2d.odin.application.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetCaseFileTest {

    private final CaseFileWorld w = new CaseFileWorld();

    private void put(Document caseDoc, Document doc, Actor by) {
        w.files.add(new CaseFileEntry(w.company, caseDoc.id(), doc.id(), by.userId(), w.clock.instant()));
    }

    @Test
    void getCaseFileListsDocumentsWithWhoAndWhen() {
        Document caseDoc = w.caseIn(w.project);
        Document doc = w.documentIn(w.project);
        put(caseDoc, doc, w.worker);

        var view = w.getCaseFile().execute(w.worker, caseDoc.id());

        assertThat(view.caseDocument().document().id()).isEqualTo(caseDoc.id());
        assertThat(view.documents()).singleElement().satisfies(d -> {
            assertThat(d.document().document().id()).isEqualTo(doc.id());
            assertThat(d.addedBy()).isEqualTo(w.worker.userId());
            assertThat(d.addedAt()).isEqualTo(w.clock.instant());
        });
    }

    @Test
    void getCaseFileCarriesThePageCount() {
        Document caseDoc = w.caseIn(w.project);
        Document doc = w.documentIn(w.project);
        w.docs.replacePages(doc.id(), java.util.List.of(page(doc, 1), page(doc, 2), page(doc, 3)));
        put(caseDoc, doc, w.worker);

        var view = w.getCaseFile().execute(w.worker, caseDoc.id());

        assertThat(view.documents().get(0).pageCount()).isEqualTo(3);
        assertThat(view.casePageCount()).isEqualTo(1);
    }

    private static com.e2d.odin.domain.document.DocumentPage page(Document d, int n) {
        return new com.e2d.odin.domain.document.DocumentPage(d.id(), n, "texto " + n, "stub", "1");
    }

    @Test
    void getCaseFileHidesUnreadDocuments() {
        Document caseDoc = w.caseIn(w.project);
        Document unread = Fixtures.document(w.docs, w.company, DocumentStatus.PENDING_READER);
        put(caseDoc, unread, w.worker);

        assertThat(w.getCaseFile().execute(w.worker, caseDoc.id()).documents()).isEmpty();
    }

    @Test
    void pendingDocumentsCountsUnreadEntriesWithoutListingThem() {
        Document caseDoc = w.caseIn(w.project);
        put(caseDoc, w.documentIn(w.project), w.worker);
        put(caseDoc, Fixtures.document(w.docs, w.company, DocumentStatus.RECEIVED), w.worker);

        var view = w.getCaseFile().execute(w.worker, caseDoc.id());

        assertThat(view.pendingDocuments()).isEqualTo(1);
        assertThat(view.documents()).hasSize(1);
    }

    @Test
    void pendingDocumentsNeverCountsDocumentsOfProjectsThePersonDoesNotSee() {
        Document caseDoc = w.caseIn(w.project);
        Document other = Fixtures.documentIn(w.docs, w.company, Fixtures.project(w.orgs, w.company), "otro");
        w.docs.save(new Document(other.id(), w.company, other.projectId(), other.title(), other.filename(),
                other.format(), other.sha256(), DocumentStatus.RECEIVED, other.failureReason(), other.paginated(),
                other.uploadedBy(), other.uploadedAt(), other.processingStartedAt(), other.attemptCount(),
                other.supersededBy(), other.withdrawnAt()));
        put(caseDoc, other, w.worker);

        assertThat(w.getCaseFile().execute(w.worker, caseDoc.id()).pendingDocuments()).isZero();
    }

    @Test
    void adminListsUnreadEntriesWithTheirStatusAndWorkerDoesNot() {
        Document caseDoc = w.caseIn(w.project);
        Document ready = w.documentIn(w.project);
        Document received = Fixtures.document(w.docs, w.company, DocumentStatus.RECEIVED);
        put(caseDoc, ready, w.worker);
        put(caseDoc, received, w.worker);
        Actor admin = Actors.companyAdmin(w.company);

        var adminView = w.getCaseFile().execute(admin, w.company, caseDoc.id());
        var workerView = w.getCaseFile().execute(w.worker, caseDoc.id());

        assertThat(adminView.documents()).extracting(d -> d.document().document().id())
                .containsExactly(ready.id(), received.id());
        assertThat(adminView.documents().get(1).document().document().status()).isEqualTo(DocumentStatus.RECEIVED);
        assertThat(workerView.documents()).extracting(d -> d.document().document().id()).containsExactly(ready.id());
        assertThat(workerView.pendingDocuments()).isEqualTo(1);
    }

    @Test
    void withdrawnEntryIsInNeitherView() {
        Document caseDoc = w.caseIn(w.project);
        Document gone = w.documentIn(w.project);
        Document goneUnread = Fixtures.document(w.docs, w.company, DocumentStatus.RECEIVED);
        put(caseDoc, gone, w.worker);
        put(caseDoc, goneUnread, w.worker);
        for (Document d : new Document[] {gone, goneUnread}) {
            w.docs.markWithdrawal(d.withWithdrawal(java.util.Optional.of(w.clock.instant()),
                    java.util.Optional.of(w.worker.userId())));
        }

        var adminView = w.getCaseFile().execute(Actors.companyAdmin(w.company), w.company, caseDoc.id());
        var workerView = w.getCaseFile().execute(w.worker, caseDoc.id());

        assertThat(adminView.documents()).isEmpty();
        assertThat(workerView.documents()).isEmpty();
        assertThat(workerView.pendingDocuments()).isZero();
    }

    @Test
    void getCaseFileShowsSupersededWithReplacement() {
        Document caseDoc = w.caseIn(w.project);
        Document old = w.documentIn(w.project);
        Document current = w.documentIn(w.project);
        w.docs.markSuperseded(w.company, old.id(), current.id(), w.clock.instant());
        put(caseDoc, old, w.worker);

        var shown = w.getCaseFile().execute(w.worker, caseDoc.id()).documents().get(0).document();

        assertThat(shown.document().superseded()).isTrue();
        assertThat(shown.replacement()).hasValueSatisfying(r -> assertThat(r.id()).isEqualTo(current.id()));
    }

    @Test
    void getCaseFileNotFoundWhenNotCase() {
        Document plain = w.documentIn(w.project);

        assertThatThrownBy(() -> w.getCaseFile().execute(w.worker, plain.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getCaseFileNotFoundWhenUnknown() {
        assertThatThrownBy(() -> w.getCaseFile().execute(w.worker, new DocumentId(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getCaseFileNotFoundWhenProjectInvisible() {
        Document caseDoc = w.caseIn(w.project);
        Actor outsider = Actors.worker(w.company);

        assertThatThrownBy(() -> w.getCaseFile().execute(outsider, caseDoc.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void workerWithoutCaseFilesCapabilityForbidden() {
        var other = Fixtures.companyWith(w.orgs, EnumSet.of(Capability.SEARCH_KNOWLEDGE));
        Actor limited = Actors.worker(other);

        assertThatThrownBy(() -> w.getCaseFile().execute(limited, new DocumentId(UUID.randomUUID())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminSeesTheCaseFileOfTheirCompanyAndOnlyThat() {
        Document caseDoc = w.caseIn(w.project);
        Actor admin = Actors.companyAdmin(w.company);

        assertThat(w.getCaseFile().execute(admin, w.company, caseDoc.id()).caseDocument().document().id())
                .isEqualTo(caseDoc.id());
        assertThatThrownBy(() -> w.getCaseFile().execute(Actors.companyAdmin(Fixtures.company(w.orgs)), w.company,
                caseDoc.id())).isInstanceOf(ForbiddenException.class);
    }
}
