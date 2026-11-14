package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.IntakeCounts;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El documento de una primera carga en la base: se guarda pendiente, se busca por huella y los lotes no se mezclan. */
class IntakeUploadIT extends AbstractPostgresIT {

    private final PostgresIntakeRepository intake = new PostgresIntakeRepository(tx);
    private CompanyId company;
    private UserId user;
    private IntakeBatch batch;

    @BeforeEach
    void seed() {
        company = newCompany();
        user = newUser(company);
        batch = IntakeBatch.open(new IntakeBatchId(UUID.randomUUID()), company, user, IntakeChannel.PANEL, NOW);
        intake.save(batch);
    }

    private Document pendingDocument(IntakeBatch inBatch, String path) {
        Document pending = new Document(new DocumentId(UUID.randomUUID()), company, Optional.empty(), "Pendiente",
                "p.pdf", DocumentFormat.PDF, UUID.randomUUID().toString(), DocumentStatus.RECEIVED, Optional.empty(),
                false, user, NOW, Optional.empty(), 0, Optional.empty(), Optional.empty())
                .withIntake(inBatch.id(), path);
        docs.save(pending);
        return pending;
    }

    @Test
    void aPendingDocumentKeepsItsBatchAndPathAndIsHiddenFromWorkers() {
        Document d = pendingDocument(batch, "Cliente/2019/a.pdf");

        Document found = docs.findBySha256ForIntake(company, batch.id(), d.sha256()).orElseThrow();
        assertThat(found.intakePending()).isTrue();
        assertThat(found.intakeBatchId()).contains(batch.id());
        assertThat(found.intakePath()).contains("Cliente/2019/a.pdf");
        assertThat(found.projectId()).isEmpty();
        assertThat(docs.findEntry(CompanyScope.of(company), ProjectVisibility.all(), d.id())).isEmpty();
        assertThat(docs.findEntryIncludingWithdrawn(CompanyScope.of(company), ProjectVisibility.all(), d.id()))
                .isPresent();
        assertThat(docs.findBySha256(company, d.sha256())).isEmpty();
    }

    @Test
    void aWithdrawnFileCountsOnlyInsideItsOwnBatch() {
        Document d = pendingDocument(batch, "a/uno.pdf");
        docs.markWithdrawal(d.withWithdrawal(Optional.of(NOW), Optional.of(user)));
        IntakeBatch second = IntakeBatch.open(new IntakeBatchId(UUID.randomUUID()), company, user,
                IntakeChannel.COMMAND, NOW);
        intake.save(batch.startReading().finishReading());
        intake.save(second);

        assertThat(docs.findBySha256ForIntake(company, batch.id(), d.sha256())).map(Document::id).contains(d.id());
        assertThat(docs.findBySha256ForIntake(company, second.id(), d.sha256())).isEmpty();
        assertThat(docs.existingSha256ForIntake(company, batch.id(), List.of(d.sha256(), "otra")))
                .isEqualTo(Set.of(d.sha256()));
        assertThat(docs.existingSha256ForIntake(company, second.id(), List.of(d.sha256()))).isEmpty();
    }

    @Test
    void theHashLookupNeverCrossesCompanies() {
        Document d = pendingDocument(batch, "a/uno.pdf");
        CompanyId other = newCompany();

        assertThat(docs.findBySha256ForIntake(other, batch.id(), d.sha256())).isEmpty();
        assertThat(docs.existingSha256ForIntake(other, batch.id(), List.of(d.sha256()))).isEmpty();
        assertThat(docs.intakeCounts(other, batch.id())).isEqualTo(new IntakeCounts(0, 0));
    }

    @Test
    void countsUploadedAndStillReading() {
        pendingDocument(batch, "a/uno.pdf");
        Document read = pendingDocument(batch, "a/dos.pdf");
        docs.save(read.markReady(true, List.of()));
        Document gone = pendingDocument(batch, "a/tres.pdf");
        docs.markWithdrawal(gone.withWithdrawal(Optional.of(NOW), Optional.of(user)));

        assertThat(docs.intakeCounts(company, batch.id())).isEqualTo(new IntakeCounts(2, 1));
    }
}
