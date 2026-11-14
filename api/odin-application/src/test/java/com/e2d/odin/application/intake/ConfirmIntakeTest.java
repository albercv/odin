package com.e2d.odin.application.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.casefile.AddToCaseFiles;
import com.e2d.odin.application.casefile.CaseFileAssignment;
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
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** D2: ningún efecto sin la confirmación del administrador, y cada efecto reutiliza el caso de uso que ya existe. */
class ConfirmIntakeTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryCaseFileRepository files = new InMemoryCaseFileRepository(docs);
    private final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(orgs);
    private final InMemoryIntakeRepository intake = new InMemoryIntakeRepository();
    private final MutableClock clock = new MutableClock();
    private final SequenceIdGenerator ids = new SequenceIdGenerator();
    private final CompanyId company = Fixtures.company(orgs);
    private final Actor admin = Actors.companyAdmin(company);
    private final List<Actor> caseFileActors = new ArrayList<>();
    private final List<List<CaseFileAssignment>> caseFileCalls = new ArrayList<>();
    private ProjectId olmo;
    private ProjectId haya;
    private IntakeBatch batch;
    private ConfirmIntakeProposals confirm;
    private DecideIntakeProposal decide;
    private DiscardIntakeDocument discard;
    private ListIntakeProposals list;
    private ListUnplacedDocuments unplaced;
    private PlaceIntakeDocument place;
    private GetIntakeBatch getBatch;

    @BeforeEach
    void inReview() {
        olmo = Fixtures.project(orgs, company);
        haya = Fixtures.project(orgs, company);
        batch = new StartIntake(intake, orgs, ids, clock).execute(admin, company, IntakeChannel.PANEL).startReading()
                .finishReading();
        intake.save(batch);
        AddToCaseFiles spy = new AddToCaseFiles(orgs, members, docs, files, ids, clock) {
            @Override
            public int execute(Actor actor, CompanyId companyId, List<CaseFileAssignment> assignments) {
                caseFileActors.add(actor);
                caseFileCalls.add(assignments);
                return super.execute(actor, companyId, assignments);
            }
        };
        IntakeEffects effects = new IntakeEffects(intake, docs, orgs, new MarkCaseDocuments(docs, files, clock),
                new SupersedeDocument(docs, clock), spy, clock);
        confirm = new ConfirmIntakeProposals(intake, effects);
        decide = new DecideIntakeProposal(intake, effects);
        discard = new DiscardIntakeDocument(docs, new WithdrawDocument(docs, clock), effects);
        list = new ListIntakeProposals(intake, docs);
        unplaced = new ListUnplacedDocuments(intake, docs);
        place = new PlaceIntakeDocument(effects);
        getBatch = new GetIntakeBatch(intake, docs, new com.e2d.odin.application.shared.InstallationSettings("t", true),
                new com.e2d.odin.application.shared.IntakeSettings(true));
    }

    private Document pending(String title) {
        Document d = Fixtures.readyDocument(docs, company, title);
        Document p = new Document(d.id(), company, Optional.empty(), title, d.filename(), d.format(), d.sha256(),
                DocumentStatus.READY, Optional.empty(), true, d.uploadedBy(), d.uploadedAt(), Optional.empty(), 0,
                Optional.empty(), Optional.empty()).withIntake(batch.id(), "Cliente/" + title);
        docs.save(p);
        return p;
    }

    private IntakeProposal propose(Document d, ProposalKind kind, ProposalConfidence confidence,
            Optional<ProjectId> project, Optional<DocumentId> target, Optional<String> caseNumber) {
        IntakeProposal p = new IntakeProposal(ids.newId(), company, batch.id(), d.id(), kind, project, target,
                caseNumber, confidence, ProposalSource.RULES, "cita de " + d.title(), DecisionState.PROPOSED,
                Optional.empty(), Optional.empty());
        intake.save(p);
        return p;
    }

    private IntakeProposal project(Document d, ProjectId in, ProposalConfidence confidence) {
        return propose(d, ProposalKind.PROJECT, confidence, Optional.of(in), Optional.empty(), Optional.empty());
    }

    private IntakeProposal pair(Document d, ProposalKind kind, Document other) {
        return propose(d, kind, ProposalConfidence.HIGH, Optional.empty(), Optional.of(other.id()), Optional.empty());
    }

    private int confirm(IntakeProposal... proposals) {
        return confirm.execute(admin, company, batch.id(), java.util.Arrays.stream(proposals).map(IntakeProposal::id).toList());
    }

    private Document stored(Document d) {
        return docs.get(d.id()).orElseThrow();
    }

    private IntakeProposal stored(IntakeProposal p) {
        return intake.proposals(company, batch.id()).stream().filter(q -> q.id().equals(p.id())).findFirst().orElseThrow();
    }

    private boolean visibleTo(Document d, ProjectVisibility visibility) {
        return docs.findById(CompanyScope.of(company), visibility, d.id()).isPresent();
    }

    @Test
    void confirmingAProjectMakesTheDocumentVisibleOnlyToThatProjectAndStampsWhoAndWhen() {
        Document d = pending("Acta");
        IntakeProposal p = project(d, olmo, ProposalConfidence.HIGH);
        assertThat(visibleTo(d, ProjectVisibility.of(Set.of(olmo)))).isFalse();
        assertThat(visibleTo(d, ProjectVisibility.all())).isFalse();

        assertThat(confirm(p)).isEqualTo(1);

        assertThat(stored(d).projectId()).contains(olmo);
        assertThat(stored(d).intakePending()).isFalse();
        assertThat(visibleTo(d, ProjectVisibility.of(Set.of(olmo)))).isTrue();
        assertThat(visibleTo(d, ProjectVisibility.of(Set.of(haya)))).isFalse();
        assertThat(visibleTo(d, ProjectVisibility.of(Set.of()))).isFalse();
        assertThat(stored(p).state()).isEqualTo(DecisionState.CONFIRMED);
        assertThat(stored(p).decidedBy()).contains(admin.userId());
        assertThat(stored(p).decidedAt()).contains(clock.instant());
    }

    @Test
    void companyWideLeavesTheDocumentWithoutProjectAndVisibleToTheWholeCompany() {
        Document d = pending("Estatutos");
        IntakeProposal p = project(d, olmo, ProposalConfidence.LOW);

        decide.execute(admin, company, batch.id(), p.id(), IntakeDecision.COMPANY_WIDE, Optional.empty());

        assertThat(stored(d).projectId()).isEmpty();
        assertThat(stored(d).intakePending()).isFalse();
        assertThat(visibleTo(d, ProjectVisibility.of(Set.of()))).isTrue();
        assertThat(stored(p).decidedBy()).contains(admin.userId());
    }

    @Test
    void dontKnowKeepsTheDocumentPendingAndTheProposalUndecided() {
        Document d = pending("Dudoso");
        IntakeProposal p = project(d, olmo, ProposalConfidence.LOW);

        decide.execute(admin, company, batch.id(), p.id(), IntakeDecision.DONT_KNOW, Optional.empty());

        assertThat(stored(d).intakePending()).isTrue();
        assertThat(stored(p).state()).isEqualTo(DecisionState.PROPOSED);
        assertThat(stored(p).decidedAt()).isEmpty();
        assertThat(intake.find(company, batch.id()).orElseThrow().state()).isEqualTo(IntakeState.IN_REVIEW);
    }

    @Test
    void correctingTheProjectPlacesTheDocumentThereAndRejectsTheSiblings() {
        Document d = pending("Ambiguo");
        IntakeProposal one = project(d, olmo, ProposalConfidence.LOW);
        IntakeProposal two = project(d, haya, ProposalConfidence.LOW);
        ProjectId third = Fixtures.project(orgs, company);

        decide.execute(admin, company, batch.id(), one.id(), IntakeDecision.CORRECT_PROJECT, Optional.of(third));

        assertThat(stored(d).projectId()).contains(third);
        assertThat(stored(one).state()).isEqualTo(DecisionState.REJECTED);
        assertThat(stored(two).state()).isEqualTo(DecisionState.REJECTED);
        assertThat(stored(two).decidedBy()).contains(admin.userId());
    }

    @Test
    void correctingNeedsAProjectOfTheCompany() {
        Document d = pending("Ambiguo");
        IntakeProposal p = project(d, olmo, ProposalConfidence.LOW);
        ProjectId foreign = Fixtures.project(orgs, Fixtures.company(orgs));

        assertThatThrownBy(() -> decide.execute(admin, company, batch.id(), p.id(), IntakeDecision.CORRECT_PROJECT,
                Optional.empty())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> decide.execute(admin, company, batch.id(), p.id(), IntakeDecision.CORRECT_PROJECT,
                Optional.of(foreign))).isInstanceOf(NotFoundException.class);
        assertThat(stored(d).intakePending()).isTrue();
    }

    @Test
    void confirmingOneProjectProposalRejectsTheOtherUndecidedOnesOfTheSameDocument() {
        Document d = pending("Ambiguo");
        IntakeProposal one = project(d, olmo, ProposalConfidence.LOW);
        IntakeProposal two = project(d, haya, ProposalConfidence.LOW);

        confirm(one);

        assertThat(stored(one).state()).isEqualTo(DecisionState.CONFIRMED);
        assertThat(stored(two).state()).isEqualTo(DecisionState.REJECTED);
        assertThat(stored(d).projectId()).contains(olmo);
    }

    @Test
    void aGroupWithTwoProjectsForTheSameDocumentIsRefusedAsAWhole() {
        Document d = pending("Ambiguo");
        Document other = pending("Otro");
        IntakeProposal one = project(d, olmo, ProposalConfidence.LOW);
        IntakeProposal two = project(d, haya, ProposalConfidence.LOW);
        IntakeProposal fine = project(other, olmo, ProposalConfidence.HIGH);

        assertThatThrownBy(() -> confirm(fine, one, two)).isInstanceOf(ConflictException.class);

        assertThat(stored(other).intakePending()).isTrue();
        assertThat(stored(fine).state()).isEqualTo(DecisionState.PROPOSED);
    }

    @Test
    void aGroupIsAllOrNothingWhenOneProposalIsAlreadyDecided() {
        Document a = pending("A");
        Document b = pending("B");
        IntakeProposal fine = project(a, olmo, ProposalConfidence.HIGH);
        IntakeProposal done = project(b, olmo, ProposalConfidence.HIGH);
        decide.execute(admin, company, batch.id(), done.id(), IntakeDecision.REJECT, Optional.empty());

        assertThatThrownBy(() -> confirm(fine, done)).isInstanceOf(ConflictException.class);

        assertThat(stored(a).intakePending()).isTrue();
        assertThat(stored(fine).state()).isEqualTo(DecisionState.PROPOSED);
    }

    @Test
    void aVisibleTwinMakesTheConfirmationA409AndChangesNothing() {
        Document d = pending("Acta");
        IntakeProposal p = project(d, olmo, ProposalConfidence.HIGH);
        Document visibleTwin = Fixtures.documentIn(docs, company, olmo, "igual");
        docs.save(new Document(visibleTwin.id(), company, Optional.of(olmo), "igual", "igual.pdf", d.format(),
                d.sha256(), DocumentStatus.READY, Optional.empty(), true, d.uploadedBy(), d.uploadedAt(),
                Optional.empty(), 0, Optional.empty(), Optional.empty()));

        assertThatThrownBy(() -> confirm(p)).isInstanceOf(ConflictException.class)
                .hasMessage("Ya hay un documento igual en ese proyecto: descarta este");

        assertThat(stored(d).intakePending()).isTrue();
        assertThat(stored(p).state()).isEqualTo(DecisionState.PROPOSED);
    }

    @Test
    void pairsAndLinksWithAnUnconfirmedProjectAreRefusedWith409() {
        Document placed = pending("Póliza nueva");
        Document unplaced = pending("Póliza vieja");
        confirm(project(placed, olmo, ProposalConfidence.HIGH));
        IntakeProposal supersedes = pair(placed, ProposalKind.SUPERSEDES, unplaced);
        IntakeProposal scan = pair(unplaced, ProposalKind.SAME_AS_SCAN, placed);
        IntakeProposal link = pair(unplaced, ProposalKind.CASE_LINK, placed);
        IntakeProposal mark = propose(unplaced, ProposalKind.CASE_MARK, ProposalConfidence.HIGH, Optional.empty(),
                Optional.empty(), Optional.of("2024-1"));

        for (IntakeProposal p : List.of(supersedes, scan, link, mark)) {
            assertThatThrownBy(() -> confirm(p)).isInstanceOf(ConflictException.class);
            assertThat(stored(p).state()).isEqualTo(DecisionState.PROPOSED);
        }
        assertThat(stored(unplaced).scanOf()).isEmpty();
        assertThat(stored(unplaced).supersededBy()).isEmpty();
        assertThat(stored(unplaced).caseDocument()).isFalse();
    }

    @Test
    void confirmedPairsSetScanOfAndTheSupersessionThroughTheExistingUseCase() {
        Document digital = pending("Contrato");
        Document scanned = pending("Contrato escaneado");
        Document old = pending("Póliza 1");
        Document fresh = pending("Póliza 2");
        IntakeProposal scan = pair(scanned, ProposalKind.SAME_AS_SCAN, digital);
        IntakeProposal supersedes = pair(fresh, ProposalKind.SUPERSEDES, old);
        confirm(project(digital, olmo, ProposalConfidence.HIGH), project(scanned, olmo, ProposalConfidence.HIGH),
                project(old, olmo, ProposalConfidence.HIGH), project(fresh, olmo, ProposalConfidence.HIGH));
        // sin documentos pendientes pero con parejas por decidir, el lote sigue abierto
        assertThat(intake.find(company, batch.id()).orElseThrow().state()).isEqualTo(IntakeState.IN_REVIEW);

        confirm(scan, supersedes);

        assertThat(stored(scanned).scanOf()).contains(digital.id());
        assertThat(stored(old).supersededBy()).contains(fresh.id());
        assertThat(stored(old).supersededAt()).contains(clock.instant());
        assertThat(stored(digital).scanOf()).isEmpty();
    }

    @Test
    void aScanOfADocumentOfAnotherProjectIsRefused() {
        Document digital = pending("Contrato");
        Document scanned = pending("Contrato escaneado");
        confirm(project(digital, olmo, ProposalConfidence.HIGH), project(scanned, haya, ProposalConfidence.HIGH));

        assertThatThrownBy(() -> confirm(pair(scanned, ProposalKind.SAME_AS_SCAN, digital)))
                .isInstanceOf(ConflictException.class);
        assertThat(stored(scanned).scanOf()).isEmpty();
    }

    @Test
    void caseMarksAndLinksGoThroughTheExistingUseCasesGroupedByCaseWithTheAdminAsActor() {
        Document card = pending("Ficha 2024-1");
        Document otherCard = pending("Ficha 2024-2");
        Document a = pending("Escrito A");
        Document b = pending("Escrito B");
        Document c = pending("Escrito C");
        IntakeProposal mark = propose(card, ProposalKind.CASE_MARK, ProposalConfidence.HIGH, Optional.empty(),
                Optional.empty(), Optional.of("2024-1"));
        IntakeProposal markOther = propose(otherCard, ProposalKind.CASE_MARK, ProposalConfidence.HIGH, Optional.empty(),
                Optional.empty(), Optional.of("2024-2"));
        confirm(project(card, olmo, ProposalConfidence.HIGH), project(otherCard, olmo, ProposalConfidence.HIGH),
                project(a, olmo, ProposalConfidence.HIGH), project(b, olmo, ProposalConfidence.HIGH),
                project(c, olmo, ProposalConfidence.HIGH));

        // la ficha se marca y sus enlaces se confirman en el mismo grupo: primero las marcas
        assertThat(confirm(pair(a, ProposalKind.CASE_LINK, card), pair(b, ProposalKind.CASE_LINK, card),
                pair(c, ProposalKind.CASE_LINK, otherCard), mark, markOther)).isEqualTo(5);

        assertThat(stored(card).caseDocument()).isTrue();
        assertThat(caseFileCalls).hasSize(1);
        assertThat(caseFileCalls.get(0)).hasSize(2).anySatisfy(x -> {
            assertThat(x.caseId()).isEqualTo(card.id());
            assertThat(x.documentIds()).containsExactlyInAnyOrder(a.id(), b.id());
        });
        assertThat(caseFileActors).containsOnly(admin);
        assertThat(files.countEntries(company, card.id())).isEqualTo(2);
        assertThat(files.countEntries(company, otherCard.id())).isEqualTo(1);
    }

    @Test
    void aLinkToAFichaThatIsNotMarkedAsCaseIsRefusedWith409() {
        Document card = pending("Ficha");
        Document a = pending("Escrito");
        confirm(project(card, olmo, ProposalConfidence.HIGH), project(a, olmo, ProposalConfidence.HIGH));

        assertThatThrownBy(() -> confirm(pair(a, ProposalKind.CASE_LINK, card))).isInstanceOf(ConflictException.class);
        assertThat(caseFileCalls).isEmpty();
    }

    @Test
    void theBatchClosesWhenNothingIsPendingAndNotBefore() {
        Document a = pending("A");
        Document b = pending("B");
        IntakeProposal pa = project(a, olmo, ProposalConfidence.HIGH);
        IntakeProposal pb = project(b, olmo, ProposalConfidence.LOW);

        confirm(pa);
        assertThat(intake.find(company, batch.id()).orElseThrow().state()).isEqualTo(IntakeState.IN_REVIEW);
        decide.execute(admin, company, batch.id(), pb.id(), IntakeDecision.DONT_KNOW, Optional.empty());
        assertThat(intake.find(company, batch.id()).orElseThrow().state()).isEqualTo(IntakeState.IN_REVIEW);
        decide.execute(admin, company, batch.id(), pb.id(), IntakeDecision.COMPANY_WIDE, Optional.empty());

        assertThat(intake.find(company, batch.id()).orElseThrow().state()).isEqualTo(IntakeState.CLOSED);
        assertThatThrownBy(() -> confirm(pa)).isInstanceOf(ConflictException.class);
    }

    @Test
    void discardingWithdrawsTheDocumentRejectsItsUndecidedProposalsAndClosesTheBatch() {
        Document a = pending("A");
        Document b = pending("B");
        IntakeProposal own = project(a, olmo, ProposalConfidence.LOW);
        IntakeProposal about = pair(b, ProposalKind.SUPERSEDES, a);
        IntakeProposal unrelated = project(b, olmo, ProposalConfidence.HIGH);
        confirm(unrelated);

        discard.execute(admin, company, batch.id(), a.id());

        assertThat(stored(a).withdrawn()).isTrue();
        assertThat(stored(own).state()).isEqualTo(DecisionState.REJECTED);
        assertThat(stored(about).state()).isEqualTo(DecisionState.REJECTED);
        assertThat(stored(unrelated).state()).isEqualTo(DecisionState.CONFIRMED);
        assertThat(intake.find(company, batch.id()).orElseThrow().state()).isEqualTo(IntakeState.CLOSED);
    }

    @Test
    void aDocumentAlreadyConfirmedOrOfAnotherBatchCannotBeDiscardedHere() {
        Document a = pending("A");
        pending("B sigue pendiente");
        confirm(project(a, olmo, ProposalConfidence.HIGH));
        Document elsewhere = Fixtures.readyDocument(docs, company, "x");

        assertThatThrownBy(() -> discard.execute(admin, company, batch.id(), a.id())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> discard.execute(admin, company, batch.id(), elsewhere.id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(stored(a).withdrawn()).isFalse();
    }

    @Test
    void listGroupsByKindProjectAndConfidenceWithTitlesAndOnlyWhatIsUndecided() {
        Document a = pending("Acta A");
        Document b = pending("Acta B");
        Document c = pending("Acta C");
        project(a, olmo, ProposalConfidence.HIGH);
        project(b, olmo, ProposalConfidence.HIGH);
        project(c, haya, ProposalConfidence.LOW);
        IntakeProposal done = project(pending("Hecho"), haya, ProposalConfidence.HIGH);
        confirm(done);

        List<IntakeProposalGroup> groups = list.execute(admin, company, batch.id(), ProposalFilter.none());

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).projectId()).contains(olmo);
        assertThat(groups.get(0).confidence()).isEqualTo(ProposalConfidence.HIGH);
        assertThat(groups.get(0).items()).extracting(IntakeProposalItem::title).containsExactly("Acta A", "Acta B");
        assertThat(groups.get(0).items().get(0).proposal().reason()).isEqualTo("cita de Acta A");
        assertThat(groups.get(1).projectId()).contains(haya);
        assertThat(list.execute(admin, company, batch.id(), new ProposalFilter(Optional.empty(),
                Optional.of(ProposalConfidence.LOW), Optional.empty()))).hasSize(1);
        assertThat(list.execute(admin, company, batch.id(), new ProposalFilter(Optional.empty(), Optional.empty(),
                Optional.of(olmo)))).hasSize(1);
        assertThat(list.execute(admin, company, batch.id(), new ProposalFilter(Optional.of(ProposalKind.CASE_MARK),
                Optional.empty(), Optional.empty()))).isEmpty();
    }

    @Test
    void anAdminOfAnotherCompanyAndAWorkerAreRefusedAndNothingChanges() {
        Document d = pending("Acta");
        IntakeProposal p = project(d, olmo, ProposalConfidence.HIGH);
        Actor other = Actors.companyAdmin(Fixtures.company(orgs));
        Actor worker = Actors.worker(company);

        for (Actor actor : List.of(other, worker)) {
            assertThatThrownBy(() -> confirm.execute(actor, company, batch.id(), List.of(p.id())))
                    .isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> decide.execute(actor, company, batch.id(), p.id(), IntakeDecision.CONFIRM,
                    Optional.empty())).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> discard.execute(actor, company, batch.id(), d.id()))
                    .isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> list.execute(actor, company, batch.id(), ProposalFilter.none()))
                    .isInstanceOf(ForbiddenException.class);
        }
        assertThat(stored(d).intakePending()).isTrue();
        assertThat(stored(p).state()).isEqualTo(DecisionState.PROPOSED);
    }

    @Test
    void theBatchOfAnotherCompanyAndUnknownProposalsAre404() {
        Document d = pending("Acta");
        IntakeProposal p = project(d, olmo, ProposalConfidence.HIGH);
        CompanyId otherCompany = Fixtures.company(orgs);
        Actor other = Actors.companyAdmin(otherCompany);

        assertThatThrownBy(() -> confirm.execute(other, otherCompany, batch.id(), List.of(p.id())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> confirm.execute(admin, company, batch.id(), List.of(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> confirm.execute(admin, company, batch.id(), List.of()))
                .isInstanceOf(ValidationException.class);
        assertThat(stored(d).intakePending()).isTrue();
    }

    @Test
    void nothingIsDecidedUntilTheBatchIsInReview() {
        IntakeBatch reading = new IntakeBatch(batch.id(), company, admin.userId(), IntakeChannel.PANEL,
                IntakeState.READING, batch.createdAt(), Optional.empty(), 0, 0);
        intake.save(reading);
        Document d = pending("Acta");
        IntakeProposal p = project(d, olmo, ProposalConfidence.HIGH);

        assertThatThrownBy(() -> confirm(p)).isInstanceOf(ConflictException.class);
        assertThat(stored(d).intakePending()).isTrue();
    }

    private Document failed(String title) {
        Document d = pending(title);
        Document f = d.markFailed("no se pudo leer");
        docs.save(f);
        return f;
    }

    private void place(Document d, Optional<ProjectId> project, boolean companyWide) {
        place.execute(admin, company, batch.id(), d.id(), project, companyWide);
    }

    @Test
    void anUnmatchedDocumentAndAFailedOneAreListedAsUnplacedButNotOneWithAProjectProposal() {
        Document unmatched = pending("Sin propuesta");
        Document broken = failed("Roto");
        Document proposed = pending("Con propuesta");
        project(proposed, olmo, ProposalConfidence.LOW);
        Document decided = pending("Propuesta rechazada");
        decide.execute(admin, company, batch.id(), project(decided, olmo, ProposalConfidence.LOW).id(),
                IntakeDecision.REJECT, Optional.empty());

        assertThat(unplaced.execute(admin, company, batch.id())).extracting(Document::id)
                .containsExactlyInAnyOrder(unmatched.id(), broken.id(), decided.id());
    }

    @Test
    void placingAnUnmatchedDocumentMakesItVisibleWhereTheAdminSaysAndClosesTheBatchWithTheLastOne() {
        Document inProject = pending("A un proyecto");
        Document everyone = pending("De toda la empresa");

        place(inProject, Optional.of(olmo), false);
        assertThat(intake.find(company, batch.id()).orElseThrow().state()).isEqualTo(IntakeState.IN_REVIEW);
        place(everyone, Optional.empty(), true);

        assertThat(stored(inProject).projectId()).contains(olmo);
        assertThat(stored(inProject).intakePending()).isFalse();
        assertThat(visibleTo(inProject, ProjectVisibility.of(Set.of(haya)))).isFalse();
        assertThat(stored(everyone).projectId()).isEmpty();
        assertThat(visibleTo(everyone, ProjectVisibility.of(Set.of()))).isTrue();
        assertThat(intake.find(company, batch.id()).orElseThrow().state()).isEqualTo(IntakeState.CLOSED);
    }

    @Test
    void placingNeedsExactlyOneOfProjectOrCompanyWideAndAProjectOfTheCompany() {
        Document d = pending("Sin propuesta");
        ProjectId foreign = Fixtures.project(orgs, Fixtures.company(orgs));

        assertThatThrownBy(() -> place(d, Optional.empty(), false)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> place(d, Optional.of(olmo), true)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> place(d, Optional.of(foreign), false)).isInstanceOf(NotFoundException.class);
        assertThat(stored(d).intakePending()).isTrue();
    }

    @Test
    void aFailedDocumentAndOneWithAProjectProposalCannotBePlacedDirectly() {
        Document broken = failed("Roto");
        Document proposed = pending("Con propuesta");
        project(proposed, olmo, ProposalConfidence.LOW);
        pending("Para que siga abierto");

        assertThatThrownBy(() -> place(broken, Optional.of(olmo), false)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> place(proposed, Optional.of(olmo), false)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("decídelo en su propuesta");
        assertThat(stored(broken).intakePending()).isTrue();
        assertThat(stored(proposed).intakePending()).isTrue();
    }

    @Test
    void aVisibleTwinMakesDirectPlacementA409() {
        Document d = pending("Acta");
        Document twin = Fixtures.documentIn(docs, company, olmo, "igual");
        docs.save(new Document(twin.id(), company, Optional.of(olmo), "igual", "igual.pdf", d.format(), d.sha256(),
                DocumentStatus.READY, Optional.empty(), true, d.uploadedBy(), d.uploadedAt(), Optional.empty(), 0,
                Optional.empty(), Optional.empty()));

        assertThatThrownBy(() -> place(d, Optional.of(olmo), false)).isInstanceOf(ConflictException.class);
        assertThat(stored(d).intakePending()).isTrue();
    }

    @Test
    void unplacedAndPlacementRefuseAWorkerAnotherCompanyAndForeignDocuments() {
        Document d = pending("Sin propuesta");
        CompanyId otherCompany = Fixtures.company(orgs);
        Document foreign = Fixtures.readyDocument(docs, company, "de otro lote");
        Actor other = Actors.companyAdmin(otherCompany);
        Actor worker = Actors.worker(company);

        for (Actor actor : List.of(Actors.companyAdmin(Fixtures.company(orgs)), worker)) {
            assertThatThrownBy(() -> unplaced.execute(actor, company, batch.id())).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> place.execute(actor, company, batch.id(), d.id(), Optional.of(olmo), false))
                    .isInstanceOf(ForbiddenException.class);
        }
        assertThatThrownBy(() -> unplaced.execute(other, otherCompany, batch.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> place.execute(other, otherCompany, batch.id(), d.id(), Optional.of(olmo), false))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> place(foreign, Optional.of(olmo), false)).isInstanceOf(NotFoundException.class);
        assertThat(stored(d).intakePending()).isTrue();
    }

    @Test
    void theBatchViewCountsUndecidedProposalsByKindAndTheUnplacedDocuments() {
        Document a = pending("A");
        Document b = pending("B");
        pending("Sin propuesta");
        project(a, olmo, ProposalConfidence.HIGH);
        IntakeProposal rejected = project(b, olmo, ProposalConfidence.LOW);
        propose(a, ProposalKind.CASE_MARK, ProposalConfidence.HIGH, Optional.empty(), Optional.empty(),
                Optional.of("2024-1"));
        decide.execute(admin, company, batch.id(), rejected.id(), IntakeDecision.REJECT, Optional.empty());

        IntakeBatchView view = getBatch.execute(admin, company, batch.id());

        assertThat(view.proposed()).containsEntry(ProposalKind.PROJECT, 1).containsEntry(ProposalKind.CASE_MARK, 1)
                .doesNotContainKey(ProposalKind.CASE_LINK);
        assertThat(view.unplaced()).isEqualTo(2);
    }
}
