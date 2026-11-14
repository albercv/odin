package com.e2d.odin.application.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.document.UploadDocument;
import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryIntakeRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryOriginalFileStore;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.StubFormatDetector;
import com.e2d.odin.application.intake.IntakeFileResult.Outcome;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntakeUploadTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryIntakeRepository intake = new InMemoryIntakeRepository();
    private final MutableClock clock = new MutableClock();
    private final SequenceIdGenerator ids = new SequenceIdGenerator();
    private final UploadDocument upload = new UploadDocument(docs, orgs, new InMemoryOriginalFileStore(),
            new StubFormatDetector(DocumentFormat.PDF), new UploadSettings(100), ids, clock);
    private final StartIntake start = new StartIntake(intake, orgs, ids, clock);
    private final AddFileToIntake addFile = new AddFileToIntake(intake, upload);
    private final CloseIntakeUpload close = new CloseIntakeUpload(intake);
    private final GetIntakeBatch get = new GetIntakeBatch(intake, docs);
    private final CompanyId company = Fixtures.company(orgs);
    private final CompanyId other = Fixtures.company(orgs);
    private final Actor admin = Actors.companyAdmin(company);
    private IntakeBatch batch;

    @BeforeEach
    void openBatch() {
        batch = start.execute(admin, company, IntakeChannel.PANEL);
    }

    private IntakeFileResult add(String path, String content) {
        return addFile.execute(admin, company, batch.id(), path, "f.pdf", content.getBytes());
    }

    @Test
    void theDocumentIsBornPendingWithoutProjectAndWithItsPath() {
        IntakeFileResult result = add("Cliente A/2019/contrato.pdf", "uno");

        Document d = result.entry().document();
        assertThat(result.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(d.intakePending()).isTrue();
        assertThat(d.projectId()).isEmpty();
        assertThat(d.intakePath()).contains("Cliente A/2019/contrato.pdf");
        assertThat(d.intakeBatchId()).contains(batch.id());
    }

    @Test
    void aWorkerNeverSeesAPendingDocument() {
        Document d = add("a/uno.pdf", "uno").entry().document();

        assertThat(docs.findById(CompanyScope.of(company), ProjectVisibility.all(), d.id())).isEmpty();
        assertThat(docs.list(CompanyScope.of(company), ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0,
                10)).isEmpty();
    }

    @Test
    void aHashAlreadyInTheCompanyCreatesNothingAndReturnsTheExistingDocument() {
        Document existing = add("a/uno.pdf", "uno").entry().document();
        IntakeBatch second = finishAndStartAnother();

        IntakeFileResult again = addFile.execute(admin, company, second.id(), "b/otra.pdf", "f.pdf", "uno".getBytes());

        assertThat(again.outcome()).isEqualTo(Outcome.ALREADY_THERE);
        assertThat(again.entry().document().id()).isEqualTo(existing.id());
        assertThat(docs.all()).hasSize(1);
    }

    @Test
    void theSameFileTwiceGivesTheSameDocument() {
        Document first = add("a/uno.pdf", "uno").entry().document();

        IntakeFileResult second = add("a/uno.pdf", "uno");

        assertThat(second.outcome()).isEqualTo(Outcome.ALREADY_THERE);
        assertThat(second.entry().document().id()).isEqualTo(first.id());
        assertThat(docs.all()).hasSize(1);
    }

    @Test
    void aFileWithdrawnInThisBatchDoesNotComeBackWhenResuming() {
        Document first = add("a/uno.pdf", "uno").entry().document();
        withdraw(first);

        IntakeFileResult resumed = add("a/uno.pdf", "uno");

        assertThat(resumed.outcome()).isEqualTo(Outcome.DISCARDED_IN_BATCH);
        assertThat(resumed.entry().document().id()).isEqualTo(first.id());
        assertThat(docs.all()).hasSize(1);
        assertThat(docs.get(first.id()).orElseThrow().withdrawn()).isTrue();
    }

    @Test
    void aFileWithdrawnOutsideTheBatchEntersAsNew() {
        Document old = add("a/uno.pdf", "uno").entry().document();
        withdraw(old);
        IntakeBatch second = finishAndStartAnother();

        IntakeFileResult again = addFile.execute(admin, company, second.id(), "b/uno.pdf", "f.pdf", "uno".getBytes());

        assertThat(again.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(again.entry().document().id()).isNotEqualTo(old.id());
        assertThat(again.entry().document().intakeBatchId()).contains(second.id());
    }

    @Test
    void knownFilesAnswersWhichHashesAreAlreadyThereWithdrawnInTheBatchIncluded() {
        Document kept = add("a/uno.pdf", "uno").entry().document();
        Document discarded = add("a/dos.pdf", "dos").entry().document();
        withdraw(discarded);

        Set<String> known = get.knownFiles(admin, company, batch.id(),
                List.of(kept.sha256(), discarded.sha256(), "no-esta"));

        assertThat(known).containsExactlyInAnyOrder(kept.sha256(), discarded.sha256());
    }

    @Test
    void closingMovesTheBatchToReadingAndNoMoreFilesAreAccepted() {
        add("a/uno.pdf", "uno");

        IntakeBatch reading = close.execute(admin, company, batch.id());

        assertThat(reading.state()).isEqualTo(IntakeState.READING);
        assertThat(intake.find(company, batch.id())).contains(reading);
        assertThat(get.execute(admin, company, batch.id()).counts().uploaded()).isEqualTo(1);
        assertThatThrownBy(() -> add("a/dos.pdf", "dos")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> close.execute(admin, company, batch.id())).isInstanceOf(ConflictException.class);
    }

    @Test
    void aSecondActiveBatchIsAConflict() {
        assertThatThrownBy(() -> start.execute(admin, company, IntakeChannel.COMMAND))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void anotherCompanyAdminAndAWorkerAreRefusedAndNothingIsCreated() {
        Actor adminOfOther = Actors.companyAdmin(other);
        Actor worker = Actors.worker(company);

        // el lote de otra empresa, pedido desde la propia: no existe
        assertThatThrownBy(() -> get.execute(adminOfOther, other, batch.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> addFile.execute(adminOfOther, other, batch.id(), "a/x.pdf", "f.pdf", "x".getBytes()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> close.execute(adminOfOther, other, batch.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> get.knownFiles(adminOfOther, other, batch.id(), List.of("x")))
                .isInstanceOf(NotFoundException.class);
        // pedido con la empresa del lote, la política de acceso ya lo rechaza
        assertThatThrownBy(() -> get.execute(adminOfOther, company, batch.id())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> start.execute(worker, company, IntakeChannel.PANEL))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> addFile.execute(worker, company, batch.id(), "a/x.pdf", "f.pdf", "x".getBytes()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> close.execute(worker, company, batch.id())).isInstanceOf(ForbiddenException.class);
        assertThat(docs.all()).isEmpty();
    }

    private void withdraw(Document d) {
        docs.markWithdrawal(d.withWithdrawal(Optional.of(clock.instant()), Optional.of(admin.userId())));
    }

    private IntakeBatch finishAndStartAnother() {
        intake.save(close.execute(admin, company, batch.id()).finishReading());
        return start.execute(admin, company, IntakeChannel.COMMAND);
    }
}
