package com.e2d.odin.application.intake;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.casefile.AddToCaseFiles;
import com.e2d.odin.application.document.MarkCaseDocuments;
import com.e2d.odin.application.document.SupersedeDocument;
import com.e2d.odin.application.document.UploadDocument;
import com.e2d.odin.application.document.WithdrawDocument;
import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryCaseFileRepository;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryIntakeRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryOriginalFileStore;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.StubFormatDetector;
import com.e2d.odin.application.intake.ProjectDecision.Action;
import com.e2d.odin.application.organization.CreateProject;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.audit.AuditOutcome;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.ProposalConfidence;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.intake.ProposalSource;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Una entrada por acción del administrador en la primera carga; las confirmaciones en lote, una por llamada. */
class IntakeAuditTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryCaseFileRepository files = new InMemoryCaseFileRepository(docs);
    private final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(orgs);
    private final InMemoryIntakeRepository intake = new InMemoryIntakeRepository();
    private final InMemoryOriginalFileStore store = new InMemoryOriginalFileStore();
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final MutableClock clock = new MutableClock();
    private final SequenceIdGenerator ids = new SequenceIdGenerator();
    private final CompanyId company = Fixtures.company(orgs);
    private final Actor admin = Actors.companyAdmin(company);
    private final ProjectId olmo = Fixtures.project(orgs, company);
    private final InstallationSettings installation = new InstallationSettings("t", true);
    private IntakeBatch batch;

    @BeforeEach
    void startBatch() {
        batch = new StartIntake(intake, orgs, ids, clock, audit).execute(admin, company, IntakeChannel.PANEL);
    }

    private AuditEntry only(AuditAction action) {
        assertThat(audit.entries).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo(action));
        return checked(audit.entries.get(0));
    }

    private AuditEntry checked(AuditEntry e) {
        assertThat(e.outcome()).isEqualTo(AuditOutcome.DONE);
        assertThat(e.actorUserId()).contains(admin.userId());
        assertThat(e.actorRole()).contains("COMPANY_ADMIN");
        assertThat(e.company()).contains(company);
        return e;
    }

    private AuditEntry.Target batchTarget() {
        return new AuditEntry.Target("INTAKE_BATCH", batch.id().value().toString(), null);
    }

    private void reading() {
        batch = batch.startReading();
        intake.save(batch);
    }

    private void inReview() {
        batch = batch.startReading().finishReading();
        intake.save(batch);
    }

    private Document pending(String title) {
        Document d = Fixtures.readyDocument(docs, company, title);
        Document p = new Document(d.id(), company, Optional.empty(), title, "a.pdf", d.format(), d.sha256(),
                DocumentStatus.READY, Optional.empty(), true, d.uploadedBy(), d.uploadedAt(), Optional.empty(), 0,
                Optional.empty(), Optional.empty()).withIntake(batch.id(), "Cliente/" + title);
        docs.save(p);
        return p;
    }

    private IntakeProposal projectProposal(Document d) {
        IntakeProposal p = new IntakeProposal(ids.newId(), company, batch.id(), d.id(), ProposalKind.PROJECT,
                Optional.of(olmo), Optional.empty(), Optional.empty(), ProposalConfidence.HIGH, ProposalSource.RULES,
                "cita", DecisionState.PROPOSED, Optional.empty(), Optional.empty());
        intake.save(p);
        return p;
    }

    private IntakeEffects effects() {
        return new IntakeEffects(intake, docs, orgs, new MarkCaseDocuments(docs, files, clock, audit),
                new SupersedeDocument(docs, clock, audit),
                new AddToCaseFiles(orgs, members, docs, files, ids, clock, audit), installation, clock);
    }

    @Test
    void startRecordsTheChannelAndTheBatch() {
        AuditEntry e = only(AuditAction.INTAKE_STARTED);

        assertThat(e.target()).contains(batchTarget());
        assertThat(e.details()).containsEntry("channel", "PANEL");
    }

    @Test
    void eachUploadedFileRecordsOneUploadEntryWithTheBatchAndNoContentOrPath() {
        var upload = new UploadDocument(docs, orgs, store, new StubFormatDetector(DocumentFormat.PDF),
                new UploadSettings(100), ids, clock, audit);
        audit.entries.clear();

        var result = new AddFileToIntake(intake, upload, audit).execute(admin, company, batch.id(),
                "Cliente A/contrato.pdf", "contrato.pdf", "contenido reservado".getBytes());

        AuditEntry e = only(AuditAction.DOCUMENT_UPLOADED);
        assertThat(e.target().orElseThrow().id()).isEqualTo(result.entry().document().id().value().toString());
        assertThat(e.details()).containsEntry("intakeBatchId", batch.id().value().toString())
                .containsEntry("duplicate", false);
        assertThat(e.toString()).doesNotContain("reservado", "Cliente A");
    }

    @Test
    void closingTheUploadRecordsIt() {
        audit.entries.clear();

        new CloseIntakeUpload(intake, audit).execute(admin, company, batch.id());

        assertThat(only(AuditAction.INTAKE_UPLOAD_CLOSED).target()).contains(batchTarget());
    }

    @Test
    void importingTheProjectListRecordsOnlyTheCount() {
        reading();
        audit.entries.clear();

        new ImportProjectList(intake, audit).execute(admin, company, batch.id(),
                "Calle Olmo 24\nCalle Haya 2".getBytes(StandardCharsets.UTF_8));

        AuditEntry e = only(AuditAction.INTAKE_PROJECT_LIST_IMPORTED);
        assertThat(e.target()).contains(batchTarget());
        assertThat(e.details()).containsEntry("count", 2);
        assertThat(e.toString()).doesNotContain("Olmo");
    }

    @Test
    void decidingCandidatesRecordsOneEntryWithTheCountsPerAction() {
        reading();
        new ImportProjectList(intake, new InMemoryAuditLog()).execute(admin, company, batch.id(),
                "Calle Olmo 24\nCalle Haya 2".getBytes(StandardCharsets.UTF_8));
        audit.entries.clear();

        new ConfirmProjectCandidates(intake, orgs, new CreateProject(orgs, ids, audit), audit).execute(admin,
                company, batch.id(), List.of(new ProjectDecision("Calle Olmo 24", Action.MERGE, Optional.of(olmo)),
                        new ProjectDecision("Calle Haya 2", Action.REJECT, Optional.empty())));

        AuditEntry e = only(AuditAction.INTAKE_PROJECTS_DECIDED);
        assertThat(e.details()).containsEntry("count", 2).containsEntry("merged", 1).containsEntry("rejected", 1)
                .containsEntry("created", 0);
    }

    @Test
    void closingTheProjectListRecordsIt() {
        reading();
        audit.entries.clear();
        var list = new ListProjectCandidates(intake, docs);

        new CloseProjectList(intake, docs, list, installation, clock, audit).execute(admin, company, batch.id());

        assertThat(only(AuditAction.INTAKE_PROJECT_LIST_CLOSED).target()).contains(batchTarget());
    }

    @Test
    void confirmingProposalsRecordsOneEntryWithCountAndIds() {
        inReview();
        IntakeProposal one = projectProposal(pending("Acta"));
        IntakeProposal two = projectProposal(pending("Informe"));
        audit.entries.clear();

        new ConfirmIntakeProposals(intake, effects(), audit).execute(admin, company, batch.id(),
                List.of(one.id(), two.id()));

        AuditEntry e = only(AuditAction.INTAKE_PROPOSALS_CONFIRMED);
        assertThat(e.target()).contains(batchTarget());
        assertThat(e.details()).containsEntry("count", 2);
        assertThat(((List<?>) e.details().get("ids")).stream().map(Object::toString).toList()).containsExactlyInAnyOrder(one.id().toString(),
                two.id().toString());
    }

    @Test
    void decidingOneProposalRecordsTheDecisionAndTheProposalId() {
        inReview();
        IntakeProposal p = projectProposal(pending("Acta"));
        audit.entries.clear();

        new DecideIntakeProposal(intake, effects(), audit).execute(admin, company, batch.id(), p.id(),
                IntakeDecision.COMPANY_WIDE, Optional.empty());

        AuditEntry e = only(AuditAction.INTAKE_PROPOSAL_DECIDED);
        assertThat(e.target()).contains(batchTarget());
        assertThat(e.details()).containsEntry("proposalId", p.id().toString())
                .containsEntry("decision", "COMPANY_WIDE");
    }

    @Test
    void discardingADocumentRecordsTheDiscardAfterTheWithdrawal() {
        inReview();
        Document d = pending("Acta");
        audit.entries.clear();

        new DiscardIntakeDocument(docs, new WithdrawDocument(docs, clock, audit), effects(), audit).execute(admin,
                company, batch.id(), d.id());

        assertThat(audit.entries).extracting(AuditEntry::action).containsExactly(AuditAction.DOCUMENT_WITHDRAWN,
                AuditAction.INTAKE_DOCUMENT_DISCARDED);
        AuditEntry e = checked(audit.entries.get(1));
        assertThat(e.target().orElseThrow().id()).isEqualTo(d.id().value().toString());
        assertThat(e.details()).containsEntry("intakeBatchId", batch.id().value().toString());
    }

    @Test
    void placingADocumentRecordsWhereItWent() {
        inReview();
        Document d = pending("Acta");
        audit.entries.clear();

        new PlaceIntakeDocument(effects(), audit).execute(admin, company, batch.id(), d.id(), Optional.of(olmo), false);

        AuditEntry e = only(AuditAction.INTAKE_DOCUMENT_PLACED);
        assertThat(e.project()).contains(olmo);
        assertThat(e.target().orElseThrow().id()).isEqualTo(d.id().value().toString());
        assertThat(e.details()).containsEntry("companyWide", false);
    }

    @Test
    void openingAPendingOriginalRecordsADownloadWithoutContent() {
        Document d = pending("Acta");
        docs.save(new Document(d.id(), company, Optional.empty(), "Acta", "acta.pdf", d.format(),
                store.store("contenido del acta".getBytes()), DocumentStatus.READY, Optional.empty(), true,
                d.uploadedBy(), d.uploadedAt(), Optional.empty(), 0, Optional.empty(), Optional.empty())
                .withIntake(batch.id(), "Cliente/acta.pdf"));
        audit.entries.clear();

        new OpenIntakeOriginal(intake, docs, store, audit).execute(admin, company, batch.id(), d.id());

        AuditEntry e = only(AuditAction.DOCUMENT_ORIGINAL_DOWNLOADED);
        assertThat(e.details()).containsEntry("intakeBatchId", batch.id().value().toString());
        assertThat(e.toString()).doesNotContain("contenido del acta");
    }

    @Test
    void closingTheBatchRecordsOneEntryWithTheBatchAndTheRejectedCountButNoContent() {
        inReview();
        Document d = pending("contrato reservado");
        IntakeProposal p = projectProposal(d);
        intake.save(p.dontKnow(admin.userId(), clock.instant()));
        audit.entries.clear();

        new CloseIntakeBatch(intake, clock, audit).execute(admin, company, batch.id());

        AuditEntry e = only(AuditAction.INTAKE_CLOSED);
        assertThat(e.target()).contains(batchTarget());
        assertThat(e.details()).containsEntry("rejected", 1);
        assertThat(e.details().toString()).doesNotContain("contrato reservado");
    }
}
