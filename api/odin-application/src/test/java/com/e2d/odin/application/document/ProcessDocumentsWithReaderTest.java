package com.e2d.odin.application.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryOriginalFileStore;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.ScriptedReaderService;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.StubFormatDetector;
import com.e2d.odin.application.port.DocumentTooLargeForReaderException;
import com.e2d.odin.application.port.ReaderFailedException;
import com.e2d.odin.application.port.ReaderUnavailableException;
import com.e2d.odin.application.port.UnreadableDocumentException;
import com.e2d.odin.application.shared.ReaderSettings;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProcessDocumentsWithReaderTest {

    private static final ReaderSettings SETTINGS = new ReaderSettings(Duration.ofMinutes(30), 3);

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryOriginalFileStore store = new InMemoryOriginalFileStore();
    private final MutableClock clock = new MutableClock();
    private final ScriptedReaderService reader = new ScriptedReaderService();
    private final CompanyId company = Fixtures.company(orgs);
    private final ProcessDocumentsWithReader processor = new ProcessDocumentsWithReader(docs, store, reader, SETTINGS,
            clock);

    private Document pendingReader() {
        return pendingReader(DocumentFormat.IMAGE);
    }

    private Document pendingReader(DocumentFormat format) {
        StubFormatDetector detector = new StubFormatDetector(format);
        Document d = new UploadDocument(docs, orgs, store, detector, new UploadSettings(), new SequenceIdGenerator(),
                clock).execute(Actors.companyAdmin(company), company, Optional.empty(), "Escaneo", "e", "x".getBytes(), false)
                .document();
        docs.save(d.awaitReader());
        return d;
    }

    private Document after(Document d) {
        return docs.get(d.id()).orElseThrow();
    }

    @Test
    void unavailableServiceClaimsNothing() {
        Document d = pendingReader();
        reader.available(false).returns("uno");

        assertThat(processor.processNext()).isFalse();

        assertThat(after(d).status()).isEqualTo(DocumentStatus.PENDING_READER);
        assertThat(after(d).attemptCount()).isZero();
        assertThat(reader.reads()).isZero();
    }

    @Test
    void readsPendingDocumentAndMarksReady() {
        Document d = pendingReader();
        reader.returns("uno", "dos");

        assertThat(processor.processNext()).isTrue();

        Document ready = after(d);
        assertThat(ready.status()).isEqualTo(DocumentStatus.READY);
        assertThat(ready.paginated()).isTrue();
        assertThat(ready.pagesWithoutText()).isEmpty();
        assertThat(ready.attemptCount()).isEqualTo(1);
        assertThat(docs.pagesOf(d.id())).extracting("number", "text", "readerName")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, "uno", "unlimited-ocr-2"),
                        org.assertj.core.groups.Tuple.tuple(2, "dos", "unlimited-ocr-2"));
    }

    @Test
    void unavailableServiceKeepsDocumentWaitingWithoutSpendingAttempts() {
        Document d = pendingReader();
        reader.fails(new ReaderUnavailableException("sin conexión", null));

        for (int i = 0; i < 5; i++) {
            assertThat(processor.processNext()).isTrue();
        }

        assertThat(after(d).status()).isEqualTo(DocumentStatus.PENDING_READER);
        assertThat(after(d).attemptCount()).isZero();
        assertThat(docs.pagesOf(d.id())).isEmpty();
    }

    @Test
    void unreadableIsFailedWithUnreadableReason() {
        Document d = pendingReader();
        reader.fails(new UnreadableDocumentException("422"));

        processor.processNext();

        assertThat(after(d).status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(after(d).failureReason()).contains(ProcessPendingDocuments.UNREADABLE);
    }

    @Test
    void tooLargeForReaderIsFailedWithItsReason() {
        Document d = pendingReader();
        reader.fails(new DocumentTooLargeForReaderException("413"));

        processor.processNext();

        assertThat(after(d).status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(after(d).failureReason()).contains(ProcessDocumentsWithReader.TOO_LARGE_FOR_READER);
        assertThat(reader.reads()).isEqualTo(1);
    }

    @Test
    void readerFailureBeforeLastAttemptReturnsToQueueKeepingAttempts() {
        Document d = pendingReader();
        reader.fails(new ReaderFailedException("500"));

        processor.processNext();
        assertThat(after(d).status()).isEqualTo(DocumentStatus.PENDING_READER);
        assertThat(after(d).attemptCount()).isEqualTo(1);

        processor.processNext();
        assertThat(after(d).status()).isEqualTo(DocumentStatus.PENDING_READER);
        assertThat(after(d).attemptCount()).isEqualTo(2);
    }

    @Test
    void failureOnLastAttemptStoresNoPages() {
        Document d = pendingReader();
        reader.fails(new ReaderFailedException("500"));

        for (int i = 0; i < 3; i++) {
            processor.processNext();
        }

        assertThat(after(d).status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(after(d).failureReason()).contains(ProcessDocumentsWithReader.READER_FAILED);
        assertThat(after(d).attemptCount()).isEqualTo(3);
        assertThat(docs.pagesOf(d.id())).isEmpty();
    }

    @Test
    void scannedDocumentWithAllBlankPagesIsFailedWithNoTextFound() {
        Document d = pendingReader(DocumentFormat.PDF);
        reader.returns(" ", "");

        processor.processNext();

        assertThat(after(d).status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(after(d).failureReason()).contains(ProcessDocumentsWithReader.NO_TEXT_FOUND);
        assertThat(docs.pagesOf(d.id())).isEmpty();
    }

    @Test
    void imageWithoutTextIsReadyWithItsPagesMarkedWithoutText() {
        Document d = pendingReader();
        reader.returns(" ");

        processor.processNext();

        assertThat(after(d).status()).isEqualTo(DocumentStatus.READY);
        assertThat(after(d).pagesWithoutText()).containsExactly(1);
        assertThat(docs.pagesOf(d.id())).hasSize(1);
    }

    @Test
    void imageWithZeroPagesIsStillFailed() {
        Document d = pendingReader();
        reader.returns();

        processor.processNext();

        assertThat(after(d).status()).isEqualTo(DocumentStatus.FAILED);
    }

    @Test
    void fourthClaimIsTooManyAttempts() {
        Document d = pendingReader();
        Document spent = after(d);
        for (int i = 0; i < 3; i++) {
            spent = spent.startReading(clock.instant()).returnToReaderQueue(true);
        }
        docs.save(spent);
        reader.returns("uno");

        assertThat(processor.processNext()).isTrue();

        assertThat(after(d).status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(after(d).failureReason()).contains(ProcessPendingDocuments.TOO_MANY_ATTEMPTS);
        assertThat(reader.reads()).isZero();
    }

    @Test
    void staleReaderProcessingIsClaimedAgain() {
        Document d = pendingReader();
        docs.save(after(d).startReading(clock.instant()));
        reader.returns("uno");

        assertThat(processor.processNext()).isFalse();
        clock.advance(SETTINGS.staleAfter());
        assertThat(processor.processNext()).isTrue();

        assertThat(after(d).status()).isEqualTo(DocumentStatus.READY);
        assertThat(after(d).attemptCount()).isEqualTo(2);
    }
}
