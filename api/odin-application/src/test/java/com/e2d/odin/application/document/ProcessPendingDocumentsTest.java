package com.e2d.odin.application.document;

import com.e2d.odin.application.fake.InMemoryAuditLog;
import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryOriginalFileStore;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.StubFormatDetector;
import com.e2d.odin.application.fake.StubTextReader;
import com.e2d.odin.application.port.DocumentTextReader;
import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.application.shared.ProcessingSettings;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UnsupportedFormatException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProcessPendingDocumentsTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryOriginalFileStore store = new InMemoryOriginalFileStore();
    private final StubFormatDetector detector = new StubFormatDetector(DocumentFormat.PDF);
    private final MutableClock clock = new MutableClock();
    private final CompanyId company = Fixtures.company(orgs);

    private ProcessPendingDocuments processor(DocumentTextReader... readers) {
        return new ProcessPendingDocuments(docs, store, List.of(readers), new ProcessingSettings(), clock);
    }

    private Document upload(DocumentFormat format) {
        detector.detects(format);
        UploadDocument upload = new UploadDocument(docs, orgs, store, detector, new UploadSettings(),
                new SequenceIdGenerator(), clock, new InMemoryAuditLog());
        return upload.execute(Actors.companyAdmin(company), company, Optional.empty(), "Manual", "m", "x".getBytes(), false)
                .document();
    }

    private StubTextReader pdfReader(String... pages) {
        return new StubTextReader(Set.of(DocumentFormat.PDF), new ReadResult(List.of(pages), true, "pdfbox", "3"));
    }

    @Test
    void pdfWithTextBecomesReady() {
        Document d = upload(DocumentFormat.PDF);

        assertThat(processor(pdfReader("uno", "dos")).processNext()).isTrue();

        assertThat(docs.get(d.id()).orElseThrow().status()).isEqualTo(DocumentStatus.READY);
        assertThat(docs.get(d.id()).orElseThrow().paginated()).isTrue();
        assertThat(docs.pagesOf(d.id())).extracting("number", "text", "readerName")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, "uno", "pdfbox"),
                        org.assertj.core.groups.Tuple.tuple(2, "dos", "pdfbox"));
    }

    @Test
    void pdfWithoutTextAwaitsReaderWithZeroAttempts() {
        Document d = upload(DocumentFormat.PDF);

        processor(pdfReader(" ", "")).processNext();

        Document after = docs.get(d.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(DocumentStatus.PENDING_READER);
        assertThat(after.attemptCount()).isZero();
        assertThat(docs.pagesOf(d.id())).isEmpty();
    }

    @Test
    void mixedPdfIsReadyWithItsPagesWithoutText() {
        Document d = upload(DocumentFormat.PDF);

        processor(pdfReader("uno", " ", "tres", "")).processNext();

        Document after = docs.get(d.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(DocumentStatus.READY);
        assertThat(after.pagesWithoutText()).containsExactly(2, 4);
        assertThat(docs.pagesOf(d.id())).hasSize(4);
    }

    @Test
    void imageWithoutLocalReaderAwaitsReaderWithZeroAttempts() {
        Document d = upload(DocumentFormat.IMAGE);

        processor(pdfReader("uno")).processNext();

        Document after = docs.get(d.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(DocumentStatus.PENDING_READER);
        assertThat(after.attemptCount()).isZero();
    }

    private Document failWith(RuntimeException error) {
        Document d = upload(DocumentFormat.PDF);
        processor(new StubTextReader(Set.of(DocumentFormat.PDF), error)).processNext();
        Document after = docs.get(d.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(DocumentStatus.FAILED);
        return after;
    }

    @Test
    void unreadableDocumentStoresGenericReasonWithoutInternalText() {
        Document after = failWith(new UnsupportedFormatException("PDFBox: /ruta/interna corrupta"));

        assertThat(after.failureReason()).contains(ProcessPendingDocuments.UNREADABLE);
    }

    @Test
    void unexpectedReaderErrorStoresReaderUnavailableWithoutInternalText() {
        Document after = failWith(new IllegalStateException("SQL interno /var/lib/odin/originals/ab"));

        assertThat(after.failureReason()).contains(ProcessPendingDocuments.READER_UNAVAILABLE);
    }

    @Test
    void errorWithoutMessageNeverStoresNull() {
        Document after = failWith(new NullPointerException());

        assertThat(after.failureReason()).contains(ProcessPendingDocuments.READER_UNAVAILABLE);
    }

    private void crashedClaims(int times) {
        for (int i = 0; i < times; i++) {
            docs.claimNextForProcessing(clock.instant(), Duration.ofMinutes(15));
            clock.advance(Duration.ofMinutes(16));
        }
    }

    @Test
    void thirdClaimStillProcesses() {
        Document d = upload(DocumentFormat.PDF);
        crashedClaims(2);

        processor(pdfReader("uno")).processNext();

        assertThat(docs.get(d.id()).orElseThrow().status()).isEqualTo(DocumentStatus.READY);
    }

    @Test
    void fourthClaimFailsAfterThreeAttempts() {
        Document d = upload(DocumentFormat.PDF);
        crashedClaims(3);

        assertThat(processor(pdfReader("uno")).processNext()).isTrue();

        Document after = docs.get(d.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(after.failureReason()).contains(ProcessPendingDocuments.TOO_MANY_ATTEMPTS);
        assertThat(docs.pagesOf(d.id())).isEmpty();
    }

    @Test
    void retryAfterTooManyAttemptsProcessesAgain() {
        Document d = upload(DocumentFormat.PDF);
        crashedClaims(3);
        ProcessPendingDocuments processor = processor(pdfReader("uno"));
        processor.processNext();

        new RetryDocument(docs, new InMemoryAuditLog()).execute(Actors.companyAdmin(company), d.id());
        processor.processNext();

        assertThat(docs.get(d.id()).orElseThrow().status()).isEqualTo(DocumentStatus.READY);
    }

    @Test
    void nonPaginatedStoresSinglePage() {
        Document d = upload(DocumentFormat.PLAIN_TEXT);
        var reader = new StubTextReader(Set.of(DocumentFormat.PLAIN_TEXT),
                new ReadResult(List.of("todo el texto"), false, "text", "1"));

        processor(reader).processNext();

        assertThat(docs.get(d.id()).orElseThrow().paginated()).isFalse();
        assertThat(docs.pagesOf(d.id())).hasSize(1);
        assertThat(docs.pagesOf(d.id()).get(0).number()).isEqualTo(1);
    }

    @Test
    void returnsFalseWhenNothingPending() {
        assertThat(processor(pdfReader("uno")).processNext()).isFalse();
    }
}
