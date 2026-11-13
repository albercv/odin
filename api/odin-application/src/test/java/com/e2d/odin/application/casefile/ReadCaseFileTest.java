package com.e2d.odin.application.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReadCaseFileTest {

    private final CaseFileWorld w = new CaseFileWorld();

    private Document withPages(Document d, int count, int chars) {
        List<DocumentPage> pages = new ArrayList<>();
        for (int n = 1; n <= count; n++) {
            pages.add(new DocumentPage(d.id(), n, chars == 0 ? "" : "x".repeat(chars), "stub", "1"));
        }
        w.docs.replacePages(d.id(), pages);
        return d;
    }

    private void put(Document caseDoc, Document doc) {
        w.files.add(new CaseFileEntry(w.company, caseDoc.id(), doc.id(), w.worker.userId(), w.clock.instant()));
        w.clock.advance(Duration.ofMinutes(1));
    }

    private CaseFileText read(Document caseDoc, int budget, Optional<String> cursor) {
        return w.readCaseFile(budget).execute(w.worker, caseDoc.id(), cursor);
    }

    @Test
    void chunksRespectBudget() {
        Document caseDoc = withPages(w.caseIn(w.project), 3, 60);

        var first = read(caseDoc, 100, Optional.empty());

        assertThat(first.pages()).hasSize(1);
        assertThat(first.complete()).isFalse();
        assertThat(first.nextCursor()).isPresent();
        assertThat(first.pagesTotal()).isEqualTo(3);
        assertThat(first.pagesRead()).isEqualTo(1);
    }

    @Test
    void oversizedPageGoesAlone() {
        Document caseDoc = withPages(w.caseIn(w.project), 1, 500);
        Document doc = withPages(w.documentIn(w.project), 1, 10);
        put(caseDoc, doc);

        var first = read(caseDoc, 100, Optional.empty());

        assertThat(first.pages()).singleElement().satisfies(p -> assertThat(p.text()).hasSize(500));
        assertThat(first.complete()).isFalse();
    }

    @Test
    void cursorResumesWhereItStopped() {
        Document caseDoc = withPages(w.caseIn(w.project), 3, 60);

        var first = read(caseDoc, 100, Optional.empty());
        var second = read(caseDoc, 100, first.nextCursor());

        assertThat(second.pages()).singleElement().satisfies(p -> assertThat(p.pageNumber()).isEqualTo(2));
        assertThat(second.pagesRead()).isEqualTo(2);
    }

    @Test
    void completeOnLastChunk() {
        Document caseDoc = withPages(w.caseIn(w.project), 2, 60);
        var first = read(caseDoc, 100, Optional.empty());

        var last = read(caseDoc, 100, first.nextCursor());

        assertThat(last.complete()).isTrue();
        assertThat(last.nextCursor()).isEmpty();
        assertThat(last.documentsRead()).isEqualTo(last.documentsTotal());
        assertThat(last.pagesRead()).isEqualTo(last.pagesTotal());
    }

    @Test
    void unreadDocumentIsSkipped() {
        Document caseDoc = withPages(w.caseIn(w.project), 1, 10);
        Document unread = Fixtures.document(w.docs, w.company, DocumentStatus.PENDING_READER);
        put(caseDoc, unread);

        var all = read(caseDoc, 1000, Optional.empty());

        assertThat(all.pages()).extracting(CaseFileTextPage::documentId).containsOnly(caseDoc.id());
        assertThat(all.documentsTotal()).isEqualTo(1);
    }

    @Test
    void emptyPageMarkedUnread() {
        Document caseDoc = withPages(w.caseIn(w.project), 1, 0);

        var all = read(caseDoc, 1000, Optional.empty());

        assertThat(all.pages()).singleElement().satisfies(p -> assertThat(p.unread()).isTrue());
    }

    @Test
    void caseFirstThenByAddedAt() {
        Document caseDoc = withPages(w.caseIn(w.project), 1, 10);
        Document early = withPages(w.documentIn(w.project), 1, 10);
        Document late = withPages(w.documentIn(w.project), 1, 10);
        put(caseDoc, early);
        put(caseDoc, late);

        var all = read(caseDoc, 1000, Optional.empty());

        assertThat(all.pages()).extracting(CaseFileTextPage::documentId)
                .containsExactly(caseDoc.id(), early.id(), late.id());
        assertThat(all.complete()).isTrue();
    }

    @Test
    void invalidCursorIsValidationError() {
        Document caseDoc = withPages(w.caseIn(w.project), 1, 10);

        assertThatThrownBy(() -> read(caseDoc, 100, Optional.of("no-es-un-cursor")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void pendingDocumentsAreCountedNotListed() {
        Document caseDoc = withPages(w.caseIn(w.project), 1, 10);
        put(caseDoc, Fixtures.document(w.docs, w.company, DocumentStatus.RECEIVED));

        var all = read(caseDoc, 1000, Optional.empty());

        assertThat(all.pendingDocuments()).isEqualTo(1);
        assertThat(all.pages()).hasSize(1);
        assertThat(all.complete()).isTrue();
    }
}
