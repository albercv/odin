package com.e2d.odin.domain.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    private static Document document(Optional<DocumentId> supersededBy, Optional<Instant> supersededAt) {
        return new Document(new DocumentId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()), Optional.empty(),
                "Póliza", "poliza.pdf", DocumentFormat.PDF, "sha", DocumentStatus.READY, Optional.empty(), true,
                new UserId(UUID.randomUUID()), NOW, Optional.empty(), 0, supersededBy, supersededAt);
    }

    @Test
    void supersessionNeedsBothFieldsOrNeither() {
        DocumentId other = new DocumentId(UUID.randomUUID());
        assertThatThrownBy(() -> document(Optional.of(other), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> document(Optional.empty(), Optional.of(NOW)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(document(Optional.of(other), Optional.of(NOW)).superseded()).isTrue();
        assertThat(document(Optional.empty(), Optional.empty()).superseded()).isFalse();
    }

    @Test
    void documentCannotSupersedeItself() {
        Document d = document(Optional.empty(), Optional.empty());
        assertThatThrownBy(() -> d.withSupersession(Optional.of(d.id()), Optional.of(NOW)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stateChangesKeepTheMark() {
        DocumentId other = new DocumentId(UUID.randomUUID());
        Document marked = document(Optional.of(other), Optional.of(NOW));
        for (Document changed : new Document[] {marked.startProcessing(NOW), marked.markReady(false, List.of()),
                marked.markFailed("roto"), marked.awaitReader(), marked.startReading(NOW),
                marked.returnToReaderQueue(true), marked.requeue(), marked.resetAttempts()}) {
            assertThat(changed.supersededBy()).contains(other);
            assertThat(changed.supersededAt()).contains(NOW);
        }
    }

    private static Document withState(DocumentStatus status, int attempts) {
        Document d = document(Optional.empty(), Optional.empty());
        return new Document(d.id(), d.companyId(), d.projectId(), d.title(), d.filename(), d.format(), d.sha256(),
                status, Optional.of("antes"), d.paginated(), d.uploadedBy(), NOW, Optional.empty(), attempts,
                Optional.empty(), Optional.empty());
    }

    @Test
    void awaitReaderResetsAttempts() {
        Document waiting = withState(DocumentStatus.PROCESSING, 3).awaitReader();

        assertThat(waiting.status()).isEqualTo(DocumentStatus.PENDING_READER);
        assertThat(waiting.attemptCount()).isZero();
        assertThat(waiting.failureReason()).isEmpty();
    }

    @Test
    void startReadingCountsAttempt() {
        Document reading = withState(DocumentStatus.PENDING_READER, 1).startReading(NOW);

        assertThat(reading.status()).isEqualTo(DocumentStatus.READER_PROCESSING);
        assertThat(reading.attemptCount()).isEqualTo(2);
        assertThat(reading.processingStartedAt()).contains(NOW);
    }

    @Test
    void returnToReaderQueueWithoutCountingUndoesTheAttempt() {
        Document reading = withState(DocumentStatus.PENDING_READER, 1).startReading(NOW);

        assertThat(reading.returnToReaderQueue(false).attemptCount()).isEqualTo(1);
        assertThat(reading.returnToReaderQueue(true).attemptCount()).isEqualTo(2);
        assertThat(reading.returnToReaderQueue(false).status()).isEqualTo(DocumentStatus.PENDING_READER);
    }

    @Test
    void markReadyKeepsPagesWithoutText() {
        Document ready = withState(DocumentStatus.PROCESSING, 1).markReady(true, List.of(2, 4));

        assertThat(ready.pagesWithoutText()).containsExactly(2, 4);
        assertThat(withState(DocumentStatus.PROCESSING, 1).pagesWithoutText()).isEmpty();
    }
}
