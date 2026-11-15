package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.fake.InMemoryAuditLog;
import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.document.UploadDocument;
import com.e2d.odin.application.document.UploadOutcome;
import com.e2d.odin.application.document.UploadToProject;
import com.e2d.odin.application.fake.InMemoryOriginalFileStore;
import com.e2d.odin.application.fake.StubFormatDetector;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.IntakeCounts;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
    void theClosedProjectListKeepsWhoAndWhen() {
        IntakeBatch closed = batch.startReading().closeProjectList(user, NOW.plusSeconds(5));
        intake.save(closed);

        IntakeBatch found = intake.find(company, batch.id()).orElseThrow();
        assertThat(found.projectListClosedBy()).contains(user);
        assertThat(found.projectListClosedAt()).contains(NOW.plusSeconds(5));
        assertThat(intake.find(company, batch.id()).map(IntakeBatch::state)).contains(IntakeState.READING);
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
    void theCountersOfAlreadyThereAndDiscardedPersistAndNeverCrossCompanies() {
        intake.countFile(company, batch.id(), false);
        intake.countFile(company, batch.id(), false);
        intake.countFile(company, batch.id(), true);
        intake.countFile(newCompany(), batch.id(), true);

        IntakeBatch stored = intake.find(company, batch.id()).orElseThrow();
        assertThat(stored.alreadyThere()).isEqualTo(2);
        assertThat(stored.discarded()).isEqualTo(1);
        intake.save(stored.startReading());
        assertThat(intake.find(company, batch.id()).orElseThrow().alreadyThere()).isEqualTo(2);
    }

    @Test
    void theHashLookupNeverCrossesCompanies() {
        Document d = pendingDocument(batch, "a/uno.pdf");
        CompanyId other = newCompany();

        assertThat(docs.findBySha256ForIntake(other, batch.id(), d.sha256())).isEmpty();
        assertThat(docs.existingSha256ForIntake(other, batch.id(), List.of(d.sha256()))).isEmpty();
        assertThat(docs.intakeCounts(other, batch.id())).isEqualTo(new IntakeCounts(0, 0, 0, 0));
    }

    @Test
    void countsUploadedAndStillReading() {
        pendingDocument(batch, "a/uno.pdf");
        Document read = pendingDocument(batch, "a/dos.pdf");
        docs.save(read.markReady(true, List.of()));
        Document gone = pendingDocument(batch, "a/tres.pdf");
        docs.markWithdrawal(gone.withWithdrawal(Optional.of(NOW), Optional.of(user)));
        docs.save(pendingDocument(batch, "a/escaneo.pdf").awaitReader());

        assertThat(docs.intakeCounts(company, batch.id())).isEqualTo(new IntakeCounts(3, 2, 3, 1));
    }

    private final UploadDocument upload = new UploadDocument(docs, orgs, new InMemoryOriginalFileStore(),
            new StubFormatDetector(DocumentFormat.PDF), new UploadSettings(1000), UUID::randomUUID,
            Clock.fixed(NOW, ZoneOffset.UTC), new InMemoryAuditLog());
    private final byte[] bytes = "mismo contenido".getBytes();

    /** El administrador sube {@link #bytes} a la primera carga y un trabajador sube lo mismo por {@code workerUpload}. */
    private void assertTheWorkerLearnsNothing(Function<Actor, UploadOutcome> workerUpload) {
        Actor admin = new Actor(user, "Admin", Role.COMPANY_ADMIN, Set.of(company));
        Actor worker = new Actor(user, "Wanda", Role.WORKER, Set.of(company));
        Document pending = upload.executeForIntake(admin, company, batch.id(), "a/uno.pdf", "uno.pdf", "uno.pdf", bytes)
                .document();

        UploadOutcome outcome = workerUpload.apply(worker);

        assertThat(outcome.duplicate()).isFalse();
        assertThat(outcome.document().id()).isNotEqualTo(pending.id());
        assertThat(docs.findEntry(CompanyScope.of(company), ProjectVisibility.all(), pending.id())).isEmpty();
        assertThat(docs.list(CompanyScope.of(company), ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0,
                10)).extracting(e -> e.document().id()).containsExactly(outcome.document().id());
        assertThat(docs.findBySha256ForIntake(company, batch.id(), pending.sha256()).orElseThrow().id())
                .isEqualTo(pending.id());
    }

    /** D1: subir lo mismo que hay pendiente no delata el documento oculto: se responde como con un duplicado que no se ve. */
    @Test
    void aWorkerUploadingTheBytesOfAPendingDocumentLearnsNothingAndTheOriginalStaysHidden() {
        assertTheWorkerLearnsNothing(worker -> upload.executeForCase(worker, company, Optional.empty(), "Mi copia",
                "copia.pdf", bytes, docs::save));
    }

    /** D1 en la subida a un proyecto del encargo 17 (por enlace o desde el chat): igual que en la de un caso. */
    @Test
    void aWorkerUploadingToAProjectTheBytesOfAPendingDocumentLearnsNothingAndTheOriginalStaysHidden() {
        Project project = orgs.findProject(company, newProject(company, "Obra")).orElseThrow();

        assertTheWorkerLearnsNothing(worker -> new UploadToProject(upload, docs).execute(worker, project, "Mi copia",
                "copia.pdf", bytes));
    }
}
