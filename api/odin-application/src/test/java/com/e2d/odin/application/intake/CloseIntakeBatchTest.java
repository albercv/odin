package com.e2d.odin.application.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.casefile.AddToCaseFiles;
import com.e2d.odin.application.document.MarkCaseDocuments;
import com.e2d.odin.application.document.SupersedeDocument;
import com.e2d.odin.application.document.WithdrawDocument;
import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryCaseFileRepository;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryIntakeRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.IntakeSettings;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.intake.ProposalConfidence;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.intake.ProposalSource;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** D-113: el administrador cierra la carga y lo que seguía en «No lo sé» queda en «Sin ubicar». */
class CloseIntakeBatchTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryCaseFileRepository files = new InMemoryCaseFileRepository(docs);
    private final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(orgs);
    private final InMemoryIntakeRepository intake = new InMemoryIntakeRepository();
    private final MutableClock clock = new MutableClock();
    private final SequenceIdGenerator ids = new SequenceIdGenerator();
    private final CompanyId company = Fixtures.company(orgs);
    private final Actor admin = Actors.companyAdmin(company);
    private final CloseIntakeBatch close = new CloseIntakeBatch(intake, clock);
    private IntakeBatch batch;
    private ProjectId olmo;
    private DecideIntakeProposal decide;
    private PlaceIntakeDocument place;
    private DiscardIntakeDocument discard;
    private GetIntakeBatch get;

    @BeforeEach
    void inReview() {
        olmo = Fixtures.project(orgs, company);
        batch = new StartIntake(intake, orgs, ids, clock).execute(admin, company, IntakeChannel.PANEL).startReading()
                .finishReading();
        intake.save(batch);
        IntakeEffects effects = new IntakeEffects(intake, docs, orgs, new MarkCaseDocuments(docs, files, clock),
                new SupersedeDocument(docs, clock), new AddToCaseFiles(orgs, members, docs, files, ids, clock),
                new InstallationSettings("t", true), clock);
        decide = new DecideIntakeProposal(intake, effects);
        place = new PlaceIntakeDocument(effects);
        discard = new DiscardIntakeDocument(docs, new WithdrawDocument(docs, clock), effects);
        get = new GetIntakeBatch(intake, docs, new InstallationSettings("t", true), new IntakeSettings(true));
    }

    private Document pending(String title) {
        Document d = Fixtures.readyDocument(docs, company, title);
        Document p = new Document(d.id(), company, Optional.empty(), title, d.filename(), d.format(), d.sha256(),
                DocumentStatus.READY, Optional.empty(), true, d.uploadedBy(), d.uploadedAt(), Optional.empty(), 0,
                Optional.empty(), Optional.empty()).withIntake(batch.id(), "Cliente/" + title);
        docs.save(p);
        return p;
    }

    private IntakeProposal proposal(Document d, ProposalKind kind) {
        IntakeProposal p = new IntakeProposal(ids.newId(), company, batch.id(), d.id(), kind,
                kind == ProposalKind.PROJECT ? Optional.of(olmo) : Optional.empty(), Optional.empty(), Optional.empty(),
                ProposalConfidence.LOW, ProposalSource.RULES, "cita", DecisionState.PROPOSED, Optional.empty(),
                Optional.empty());
        intake.save(p);
        return p;
    }

    private IntakeProposal stored(IntakeProposal p) {
        return intake.proposals(company, batch.id()).stream().filter(q -> q.id().equals(p.id())).findFirst().orElseThrow();
    }

    private IntakeBatch storedBatch() {
        return intake.find(company, batch.id()).orElseThrow();
    }

    @Test
    void closingRejectsWhatIsStillDontKnowStampsWhoAndWhenAndLeavesTheDocumentsUnplaced() {
        Document d = pending("Dudoso");
        IntakeProposal p = proposal(d, ProposalKind.PROJECT);
        decide.execute(admin, company, batch.id(), p.id(), IntakeDecision.DONT_KNOW, Optional.empty());
        clock.advance(Duration.ofHours(1));

        close.execute(admin, company, batch.id());

        assertThat(stored(p).state()).isEqualTo(DecisionState.REJECTED);
        assertThat(stored(p).decidedBy()).contains(admin.userId());
        assertThat(stored(p).decidedAt()).contains(clock.instant());
        assertThat(storedBatch().state()).isEqualTo(IntakeState.CLOSED);
        assertThat(storedBatch().closedAt()).contains(clock.instant());
        assertThat(storedBatch().closedBy()).contains(admin.userId());
        assertThat(get.execute(admin, company, batch.id()).unplaced()).isEqualTo(1);
    }

    @Test
    void aDontKnowThatIsNotAPlacementIsSimplyDiscarded() {
        IntakeProposal pair = proposal(pending("Escaneo"), ProposalKind.SAME_AS_SCAN);
        decide.execute(admin, company, batch.id(), pair.id(), IntakeDecision.DONT_KNOW, Optional.empty());

        close.execute(admin, company, batch.id());

        assertThat(stored(pair).state()).isEqualTo(DecisionState.REJECTED);
    }

    @Test
    void closingWithProposalsToDecideIsRefusedAndChangesNothing() {
        IntakeProposal dontKnow = proposal(pending("A"), ProposalKind.PROJECT);
        decide.execute(admin, company, batch.id(), dontKnow.id(), IntakeDecision.DONT_KNOW, Optional.empty());
        IntakeProposal undecided = proposal(pending("B"), ProposalKind.PROJECT);
        proposal(pending("C"), ProposalKind.COMPANY_WIDE);

        assertThatThrownBy(() -> close.execute(admin, company, batch.id())).isInstanceOf(ConflictException.class)
                .hasMessage("Quedan 2 propuestas por decidir. Decídelas antes de cerrar la carga.");

        assertThat(stored(dontKnow).state()).isEqualTo(DecisionState.DONT_KNOW);
        assertThat(stored(undecided).state()).isEqualTo(DecisionState.PROPOSED);
        assertThat(storedBatch().state()).isEqualTo(IntakeState.IN_REVIEW);
        assertThat(get.execute(admin, company, batch.id()).toDecide()).isEqualTo(2);
    }

    @Test
    void onlyABatchInReviewCanBeClosed() {
        IntakeBatch reading = batch;
        intake.save(new IntakeBatch(reading.id(), company, admin.userId(), IntakeChannel.PANEL, IntakeState.READING,
                reading.createdAt(), Optional.empty(), Optional.empty(), 0, 0, Optional.empty(), Optional.empty()));
        assertThatThrownBy(() -> close.execute(admin, company, batch.id())).isInstanceOf(ConflictException.class)
                .hasMessage("La carga solo se puede cerrar cuando está en revisión.");

        intake.save(reading.close(admin.userId(), clock.instant()));
        assertThatThrownBy(() -> close.execute(admin, company, batch.id())).isInstanceOf(ConflictException.class)
                .hasMessage("La carga solo se puede cerrar cuando está en revisión.");
    }

    @Test
    void anotherCompanyAdminAndAWorkerAreRefused() {
        for (Actor intruder : List.of(Actors.companyAdmin(Fixtures.company(orgs)), Actors.worker(company))) {
            assertThatThrownBy(() -> close.execute(intruder, company, batch.id())).isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void aClosedBatchStillLetsYouPlaceAndDiscardWhatIsUnplacedAndStaysClosed() {
        Document toPlace = pending("Uno");
        Document toDiscard = pending("Dos");
        IntakeProposal p = proposal(toPlace, ProposalKind.PROJECT);
        decide.execute(admin, company, batch.id(), p.id(), IntakeDecision.DONT_KNOW, Optional.empty());
        proposal(toDiscard, ProposalKind.CASE_MARK);
        IntakeProposal other = intake.proposals(company, batch.id()).stream()
                .filter(q -> q.kind() == ProposalKind.CASE_MARK).findFirst().orElseThrow();
        decide.execute(admin, company, batch.id(), other.id(), IntakeDecision.REJECT, Optional.empty());
        close.execute(admin, company, batch.id());

        place.execute(admin, company, batch.id(), toPlace.id(), Optional.of(olmo), false);
        discard.execute(admin, company, batch.id(), toDiscard.id());

        assertThat(docs.get(toPlace.id()).orElseThrow().intakePending()).isFalse();
        assertThat(docs.get(toPlace.id()).orElseThrow().projectId()).contains(olmo);
        assertThat(storedBatch().state()).isEqualTo(IntakeState.CLOSED);
        assertThat(get.execute(admin, company, batch.id()).unplaced()).isZero();
    }

    @Test
    void aClosedBatchStillRefusesDecidingProposals() {
        close.execute(admin, company, batch.id());
        IntakeProposal p = proposal(pending("Tarde"), ProposalKind.PROJECT);

        assertThatThrownBy(() -> decide.execute(admin, company, batch.id(), p.id(), IntakeDecision.CONFIRM,
                Optional.empty())).isInstanceOf(ConflictException.class);
    }
}
