package com.e2d.odin.application.document;

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
                new SequenceIdGenerator(), clock);
        return upload.execute(Actors.companyAdmin(company), company, Optional.empty(), "Manual", "m", "x".getBytes())
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
    void pdfWithoutTextBecomesPendingReader() {
        Document d = upload(DocumentFormat.PDF);

        processor(pdfReader(" ", "")).processNext();

        assertThat(docs.get(d.id()).orElseThrow().status()).isEqualTo(DocumentStatus.PENDING_READER);
        assertThat(docs.pagesOf(d.id())).isEmpty();
    }

    @Test
    void imageBecomesPendingReader() {
        Document d = upload(DocumentFormat.IMAGE);

        processor(pdfReader("uno")).processNext();

        assertThat(docs.get(d.id()).orElseThrow().status()).isEqualTo(DocumentStatus.PENDING_READER);
    }

    @Test
    void readerErrorBecomesFailed() {
        Document d = upload(DocumentFormat.PDF);
        var failing = new StubTextReader(Set.of(DocumentFormat.PDF), new IllegalStateException("pdf corrupto"));

        processor(failing).processNext();

        Document after = docs.get(d.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(after.failureReason()).contains("pdf corrupto");
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
        assertThat(after.failureReason()).contains("demasiados intentos");
        assertThat(docs.pagesOf(d.id())).isEmpty();
    }

    @Test
    void retryAfterTooManyAttemptsProcessesAgain() {
        Document d = upload(DocumentFormat.PDF);
        crashedClaims(3);
        ProcessPendingDocuments processor = processor(pdfReader("uno"));
        processor.processNext();

        new RetryDocument(docs).execute(Actors.companyAdmin(company), d.id());
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
