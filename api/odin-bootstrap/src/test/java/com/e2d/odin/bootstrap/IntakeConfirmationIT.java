package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.adapter.postgres.PostgresCaseFileRepository;
import com.e2d.odin.adapter.postgres.PostgresDocumentRepository;
import com.e2d.odin.adapter.postgres.PostgresIntakeRepository;
import com.e2d.odin.adapter.postgres.PostgresKnowledgeSearch;
import com.e2d.odin.adapter.postgres.PostgresOrganizationRepository;
import com.e2d.odin.adapter.postgres.PostgresProjectMemberRepository;
import com.e2d.odin.adapter.postgres.PostgresUserRepository;
import com.e2d.odin.adapter.postgres.ScopedTransactions;
import com.e2d.odin.application.casefile.AddToCaseFiles;
import com.e2d.odin.application.document.MarkCaseDocuments;
import com.e2d.odin.application.document.SupersedeDocument;
import com.e2d.odin.application.document.WithdrawDocument;
import com.e2d.odin.application.intake.CloseIntakeBatch;
import com.e2d.odin.application.intake.ConfirmIntakeProposals;
import com.e2d.odin.application.intake.DecideIntakeProposal;
import com.e2d.odin.application.intake.DiscardIntakeDocument;
import com.e2d.odin.application.intake.IntakeDecision;
import com.e2d.odin.application.intake.IntakeEffects;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.intake.ListOpenIntakeBatches;
import com.e2d.odin.application.intake.ListUnplacedDocuments;
import com.e2d.odin.application.intake.StartIntake;
import com.e2d.odin.application.intake.PlaceIntakeDocument;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.IntakeState;
import com.e2d.odin.domain.intake.ProposalConfidence;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.intake.ProposalSource;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * D1 y D-047 con el rol de la aplicación y la RLS reales: un documento pendiente no lo ve nadie; confirmado, solo los
 * trabajadores de su proyecto en su empresa. Y la confirmación es todo o nada con los casos de uso de siempre.
 */
@Testcontainers
@Timeout(120)
class IntakeConfirmationIT {

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");

    private static ScopedTransactions tx;
    private static DataSourceTransactionManager transactionManager;
    private static PostgresOrganizationRepository organizations;
    private static PostgresDocumentRepository docs;
    private static PostgresIntakeRepository intake;
    private static PostgresCaseFileRepository caseFiles;
    private static PostgresUserRepository users;
    private static ConfirmIntakeProposals confirm;
    private static DecideIntakeProposal decide;
    private static DiscardIntakeDocument discard;
    private static PlaceIntakeDocument place;
    private static ListUnplacedDocuments unplaced;
    private static CloseIntakeBatch close;
    private static ListOpenIntakeBatches listBatches;
    private static StartIntake start;

    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private Actor admin;
    private ProjectId olmo;
    private ProjectId haya;
    private IntakeBatchId batch;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .placeholders(Map.of("appPassword", "app-test-password")).load().migrate();
        DriverManagerDataSource app = new DriverManagerDataSource(PG.getJdbcUrl(), "odin_app", "app-test-password");
        tx = new ScopedTransactions(app);
        transactionManager = new DataSourceTransactionManager(app);
        organizations = new PostgresOrganizationRepository(tx);
        docs = new PostgresDocumentRepository(tx);
        intake = new PostgresIntakeRepository(tx);
        caseFiles = new PostgresCaseFileRepository(tx);
        users = new PostgresUserRepository(tx);
        Clock clock = Clock.systemUTC();
        UseCaseConfiguration config = new UseCaseConfiguration();
        IntakeEffects effects = config.intakeEffects(intake, docs, organizations,
                new MarkCaseDocuments(docs, caseFiles, clock, new NoAudit()), new SupersedeDocument(docs, clock, new NoAudit()),
                new AddToCaseFiles(organizations, new PostgresProjectMemberRepository(tx), docs, caseFiles,
                        new RandomUuidGenerator(), clock, new NoAudit()), new InstallationSettings("t", true), clock);
        confirm = config.confirmIntakeProposals(intake, effects, transactionManager, new NoAudit());
        decide = config.decideIntakeProposal(intake, effects, transactionManager, new NoAudit());
        place = config.placeIntakeDocument(effects, transactionManager, new NoAudit());
        unplaced = config.listUnplacedDocuments(intake, docs);
        discard = config.discardIntakeDocument(docs, new WithdrawDocument(docs, clock, new NoAudit()), effects,
                transactionManager, new NoAudit());
        close = config.closeIntakeBatch(intake, clock, transactionManager);
        listBatches = config.listOpenIntakeBatches(intake, docs);
        start = new StartIntake(intake, organizations, new RandomUuidGenerator(), clock, new NoAudit());
    }

    @BeforeEach
    void seed() {
        organizations.saveCompany(new Company(company, "Empresa " + company.value(),
                new SecureRandomPersonCodeGenerator().next(), EnumSet.allOf(Capability.class)));
        UserId user = new UserId(UUID.randomUUID());
        users.save(new User(user, "Ada", Optional.of("Pérez"), new SecureRandomPersonCodeGenerator().next(),
                Optional.empty(), false, true, Optional.empty()));
        admin = new Actor(user, "Ada", Role.COMPANY_ADMIN, Set.of(company));
        olmo = project("Calle Olmo 24");
        haya = project("Calle Haya 2");
        batch = new IntakeBatchId(UUID.randomUUID());
        intake.save(IntakeBatch.open(batch, company, user, IntakeChannel.PANEL, Instant.now()).startReading()
                .finishReading());
    }

    private ProjectId project(String name) {
        ProjectId id = new ProjectId(UUID.randomUUID());
        organizations.saveProject(new Project(id, company, name, true));
        return id;
    }

    private Document pending(String title, String text) {
        return pending(title, text, UUID.randomUUID().toString());
    }

    private Document pending(String title, String text, String sha) {
        Document d = new Document(new DocumentId(UUID.randomUUID()), company, Optional.empty(), title, "f.pdf",
                DocumentFormat.PDF, sha, DocumentStatus.READY, Optional.empty(), true, admin.userId(), Instant.now(),
                Optional.empty(), 0, Optional.empty(), Optional.empty()).withIntake(batch, "Cliente/" + title);
        docs.save(d);
        docs.replacePages(d.id(), List.of(new DocumentPage(d.id(), 1, text, "lector", "1")));
        return d;
    }

    private IntakeProposal propose(Document d, ProposalKind kind, Optional<ProjectId> project,
            Optional<Document> target, Optional<String> caseNumber) {
        IntakeProposal p = new IntakeProposal(UUID.randomUUID(), company, batch, d.id(), kind, project,
                target.map(Document::id), caseNumber, ProposalConfidence.HIGH, ProposalSource.RULES, "cita",
                DecisionState.PROPOSED, Optional.empty(), Optional.empty());
        intake.save(p);
        return p;
    }

    private IntakeProposal projectProposal(Document d, ProjectId in) {
        return propose(d, ProposalKind.PROJECT, Optional.of(in), Optional.empty(), Optional.empty());
    }

    private int confirm(IntakeProposal... proposals) {
        return confirm.execute(admin, company, batch, java.util.Arrays.stream(proposals).map(IntakeProposal::id).toList());
    }

    private Document stored(Document d) {
        return docs.findEntryIncludingWithdrawn(CompanyScope.of(company), ProjectVisibility.all(), d.id()).orElseThrow()
                .document();
    }

    private IntakeProposal stored(IntakeProposal p) {
        return intake.proposals(company, batch).stream().filter(q -> q.id().equals(p.id())).findFirst().orElseThrow();
    }

    /** Lo que ve un trabajador en cada ruta de lectura: lista, ficha, página y búsqueda. */
    private boolean seenBy(CompanyScope scope, ProjectVisibility visibility, Document d, String term) {
        boolean listed = docs.list(scope, visibility, Optional.empty(), Optional.empty(), 0, 100).stream()
                .anyMatch(e -> e.document().id().equals(d.id()));
        boolean entry = docs.findEntry(scope, visibility, d.id()).isPresent();
        boolean page = docs.findPage(scope, visibility, d.id(), 1).isPresent();
        boolean searched = new PostgresKnowledgeSearch(tx).search(new SearchQuery(List.of(term), scope, visibility,
                Optional.empty(), 10, admin.userId(), Optional.empty())).hits().stream()
                .anyMatch(h -> h.title().equals(d.title()));
        assertThat(List.of(listed, entry, page, searched)).as("las rutas coinciden").containsOnly(listed);
        return listed;
    }

    @Test
    void aPendingDocumentIsSeenByNobodyAndOnceConfirmedOnlyByTheWorkersOfItsProject() {
        Document d = pending("Acta zafiro", "baliza zafiro cuadrante");
        IntakeProposal p = projectProposal(d, olmo);
        CompanyScope mine = CompanyScope.of(company);
        CompanyScope other = CompanyScope.of(new CompanyId(UUID.randomUUID()));
        ProjectVisibility inOlmo = ProjectVisibility.of(Set.of(olmo));
        ProjectVisibility inHaya = ProjectVisibility.of(Set.of(haya));

        for (ProjectVisibility v : List.of(ProjectVisibility.all(), inOlmo, inHaya, ProjectVisibility.of(Set.of()))) {
            assertThat(seenBy(mine, v, d, "zafiro")).as("pendiente, antes de confirmar").isFalse();
        }

        confirm(p);

        assertThat(seenBy(mine, inOlmo, d, "zafiro")).isTrue();
        assertThat(seenBy(mine, ProjectVisibility.all(), d, "zafiro")).isTrue();
        assertThat(seenBy(mine, inHaya, d, "zafiro")).as("otro proyecto de la empresa").isFalse();
        assertThat(seenBy(mine, ProjectVisibility.of(Set.of()), d, "zafiro")).as("sin proyectos").isFalse();
        assertThat(seenBy(other, inOlmo, d, "zafiro")).as("otra empresa con el mismo proyecto (D-014)").isFalse();
        assertThat(stored(p).decidedBy()).contains(admin.userId());
        assertThat(stored(p).decidedAt()).isPresent();
    }

    @Test
    void companyWideIsSeenByTheWholeCompanyAndByNoOtherCompany() {
        Document d = pending("Estatutos berilo", "baliza berilo");
        IntakeProposal p = projectProposal(d, olmo);
        CompanyScope mine = CompanyScope.of(company);

        decide.execute(admin, company, batch, p.id(), IntakeDecision.COMPANY_WIDE, Optional.empty());

        assertThat(stored(d).projectId()).isEmpty();
        assertThat(seenBy(mine, ProjectVisibility.of(Set.of()), d, "berilo")).isTrue();
        assertThat(seenBy(mine, ProjectVisibility.of(Set.of(haya)), d, "berilo")).isTrue();
        assertThat(seenBy(CompanyScope.of(new CompanyId(UUID.randomUUID())), ProjectVisibility.all(), d, "berilo"))
                .isFalse();
    }

    @Test
    void dontKnowKeepsTheDocumentHiddenFromEveryone() {
        Document d = pending("Dudoso cuarzo", "baliza cuarzo");
        IntakeProposal p = projectProposal(d, olmo);

        decide.execute(admin, company, batch, p.id(), IntakeDecision.DONT_KNOW, Optional.empty());

        assertThat(seenBy(CompanyScope.of(company), ProjectVisibility.all(), d, "cuarzo")).isFalse();
        assertThat(stored(d).intakePending()).isTrue();
        assertThat(stored(p).state()).isEqualTo(DecisionState.DONT_KNOW);
        assertThat(stored(p).decidedBy()).contains(admin.userId());
    }

    @Test
    void aGroupIsAllOrNothingInTheDatabaseWhenALaterDocumentHasAVisibleTwin() {
        Document first = pending("Primero", "uno");
        Document second = pending("Segundo", "dos", "huella-repetida");
        IntakeProposal one = projectProposal(first, olmo);
        IntakeProposal two = projectProposal(second, olmo);
        // alguien subió el mismo fichero al proyecto después, ya visible: el índice único de huella lo impide
        docs.save(new Document(new DocumentId(UUID.randomUUID()), company, Optional.of(olmo), "Gemelo", "f.pdf",
                DocumentFormat.PDF, "huella-repetida", DocumentStatus.READY, Optional.empty(), true, admin.userId(),
                Instant.now(), Optional.empty(), 0, Optional.empty(), Optional.empty()));

        assertThatThrownBy(() -> confirm(one, two)).isInstanceOf(ConflictException.class)
                .hasMessage("Ya hay un documento igual en ese proyecto: descarta este");

        assertThat(stored(first).intakePending()).isTrue();
        assertThat(stored(first).projectId()).isEmpty();
        assertThat(stored(one).state()).isEqualTo(DecisionState.PROPOSED);
        assertThat(stored(two).state()).isEqualTo(DecisionState.PROPOSED);
    }

    @Test
    void pairsAndLinksRunThroughTheExistingUseCasesInsideTheTransaction() {
        Document digital = pending("Contrato", "contrato");
        Document scanned = pending("Contrato escaneado", "contrato");
        Document old = pending("Póliza 1", "poliza uno");
        Document fresh = pending("Póliza 2", "poliza dos");
        Document card = pending("Ficha 2024-1", "ficha");
        Document linked = pending("Escrito", "escrito");
        IntakeProposal scan = propose(scanned, ProposalKind.SAME_AS_SCAN, Optional.empty(), Optional.of(digital),
                Optional.empty());
        IntakeProposal supersedes = propose(fresh, ProposalKind.SUPERSEDES, Optional.empty(), Optional.of(old),
                Optional.empty());
        IntakeProposal mark = propose(card, ProposalKind.CASE_MARK, Optional.empty(), Optional.empty(),
                Optional.of("2024-1"));
        IntakeProposal link = propose(linked, ProposalKind.CASE_LINK, Optional.empty(), Optional.of(card),
                Optional.of("2024-1"));
        assertThatThrownBy(() -> confirm(scan)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> confirm(link, mark)).isInstanceOf(ConflictException.class);
        assertThat(stored(card).caseDocument()).isFalse();

        confirm(projectProposal(digital, olmo), projectProposal(scanned, olmo), projectProposal(old, olmo),
                projectProposal(fresh, olmo), projectProposal(card, olmo), projectProposal(linked, olmo));
        assertThat(intake.find(company, batch).orElseThrow().state()).isEqualTo(IntakeState.IN_REVIEW);
        confirm(scan, supersedes, mark, link);

        assertThat(stored(scanned).scanOf()).contains(digital.id());
        assertThat(stored(old).supersededBy()).contains(fresh.id());
        assertThat(stored(card).caseDocument()).isTrue();
        assertThat(caseFiles.countEntries(company, card.id())).isEqualTo(1);
        assertThat(intake.find(company, batch).orElseThrow().state()).isEqualTo(IntakeState.CLOSED);
    }

    @Test
    void discardingWithdrawsTheDocumentAndRejectsItsProposalsAtomically() {
        Document keep = pending("Se queda", "se queda");
        Document gone = pending("Se descarta", "se descarta");
        IntakeProposal keepProposal = projectProposal(keep, olmo);
        IntakeProposal goneProposal = projectProposal(gone, olmo);

        discard.execute(admin, company, batch, gone.id());

        assertThat(stored(gone).withdrawn()).isTrue();
        // con el documento retirado la RLS oculta su propuesta a la bandeja: se mira con el propietario
        assertThat(JdbcClient.create(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()))
                .sql("SELECT state FROM intake_proposal WHERE id = :id").param("id", goneProposal.id())
                .query(String.class).single()).isEqualTo("REJECTED");
        assertThat(intake.proposals(company, batch)).extracting(IntakeProposal::id).containsExactly(keepProposal.id());
        assertThat(stored(keepProposal).state()).isEqualTo(DecisionState.PROPOSED);
        assertThat(seenBy(CompanyScope.of(company), ProjectVisibility.all(), gone, "descarta")).isFalse();
        confirm(keepProposal);
        assertThat(intake.find(company, batch).orElseThrow().state()).isEqualTo(IntakeState.CLOSED);
    }

    @Test
    void unplacedDocumentsIncludingFailedOnesAreListedAndPlacingThemFollowsD047() {
        Document unmatched = pending("Sin propuesta zafiro", "baliza zafiro");
        Document broken = pending("Roto", "nada");
        docs.save(docs.findEntryIncludingWithdrawn(CompanyScope.of(company), ProjectVisibility.all(), broken.id())
                .orElseThrow().document().markFailed("no se pudo leer"));
        projectProposal(pending("Con propuesta", "otra"), olmo);
        Document wide = pending("Para toda la empresa berilo", "baliza berilo");

        assertThat(unplaced.execute(admin, company, batch)).extracting(Document::id)
                .containsExactlyInAnyOrder(unmatched.id(), broken.id(), wide.id());
        assertThatThrownBy(() -> place.execute(admin, company, batch, broken.id(), Optional.of(olmo), false))
                .isInstanceOf(ConflictException.class);
        place.execute(admin, company, batch, unmatched.id(), Optional.of(olmo), false);
        place.execute(admin, company, batch, wide.id(), Optional.empty(), true);

        CompanyScope mine = CompanyScope.of(company);
        assertThat(seenBy(mine, ProjectVisibility.of(Set.of(olmo)), unmatched, "zafiro")).isTrue();
        assertThat(seenBy(mine, ProjectVisibility.of(Set.of(haya)), unmatched, "zafiro")).isFalse();
        assertThat(seenBy(CompanyScope.of(new CompanyId(UUID.randomUUID())), ProjectVisibility.all(), unmatched,
                "zafiro")).isFalse();
        assertThat(seenBy(mine, ProjectVisibility.of(Set.of()), wide, "berilo")).isTrue();
        assertThat(seenBy(mine, ProjectVisibility.of(Set.of(haya)), broken, "nada")).isFalse();
    }

    @Test
    void aDocumentOfAnotherBatchOrCompanyCannotBePlacedAndTheLastPlacementClosesTheBatch() {
        Document mine = pending("Mío", "mio");
        CompanyId otherCompany = new CompanyId(UUID.randomUUID());
        organizations.saveCompany(new Company(otherCompany, "Otra", new SecureRandomPersonCodeGenerator().next(),
                EnumSet.allOf(Capability.class)));
        Actor otherAdmin = new Actor(admin.userId(), "Ada", Role.COMPANY_ADMIN, Set.of(otherCompany));

        assertThatThrownBy(() -> place.execute(otherAdmin, otherCompany, batch, mine.id(), Optional.empty(), true))
                .isInstanceOf(com.e2d.odin.domain.shared.NotFoundException.class);
        assertThat(stored(mine).intakePending()).isTrue();

        place.execute(admin, company, batch, mine.id(), Optional.empty(), true);

        assertThat(intake.find(company, batch).orElseThrow().state()).isEqualTo(IntakeState.CLOSED);
    }

    @Test
    void closingTheBatchLeavesDontKnowUnplacedHiddenFromWorkersUntilPlacedInTheClosedBatch() {
        Document d = pending("Dudoso rubi", "baliza rubi");
        IntakeProposal p = projectProposal(d, olmo);
        decide.execute(admin, company, batch, p.id(), IntakeDecision.DONT_KNOW, Optional.empty());

        close.execute(admin, company, batch);

        IntakeBatch closed = intake.find(company, batch).orElseThrow();
        assertThat(closed.state()).isEqualTo(IntakeState.CLOSED);
        assertThat(closed.closedBy()).contains(admin.userId());
        assertThat(closed.closedAt()).isPresent();
        assertThat(stored(p).state()).isEqualTo(DecisionState.REJECTED);
        assertThat(stored(p).decidedBy()).contains(admin.userId());
        assertThat(unplaced.execute(admin, company, batch)).extracting(Document::id).containsExactly(d.id());
        assertThat(listBatches.execute(admin, company)).extracting(IntakeBatch::id).contains(batch);
        CompanyScope mine = CompanyScope.of(company);
        for (ProjectVisibility v : List.of(ProjectVisibility.all(), ProjectVisibility.of(Set.of(olmo)),
                ProjectVisibility.of(Set.of()))) {
            assertThat(seenBy(mine, v, d, "rubi")).as("sin colocar, tras cerrar").isFalse();
        }

        place.execute(admin, company, batch, d.id(), Optional.of(olmo), false);

        assertThat(seenBy(mine, ProjectVisibility.of(Set.of(olmo)), d, "rubi")).isTrue();
        assertThat(seenBy(mine, ProjectVisibility.of(Set.of(haya)), d, "rubi")).isFalse();
        assertThat(intake.find(company, batch).orElseThrow().state()).isEqualTo(IntakeState.CLOSED);
        assertThat(listBatches.execute(admin, company)).extracting(IntakeBatch::id).doesNotContain(batch);
    }

    @Test
    void closingWithAProposalToDecideChangesNothingInTheDatabase() {
        Document undecided = pending("Sin decidir", "x");
        Document dontKnow = pending("No lo sé", "y");
        IntakeProposal open = projectProposal(undecided, olmo);
        IntakeProposal unknown = projectProposal(dontKnow, olmo);
        decide.execute(admin, company, batch, unknown.id(), IntakeDecision.DONT_KNOW, Optional.empty());

        assertThatThrownBy(() -> close.execute(admin, company, batch)).isInstanceOf(ConflictException.class);

        assertThat(stored(unknown).state()).isEqualTo(DecisionState.DONT_KNOW);
        assertThat(stored(open).state()).isEqualTo(DecisionState.PROPOSED);
        assertThat(intake.find(company, batch).orElseThrow().state()).isEqualTo(IntakeState.IN_REVIEW);
    }

    @Test
    void aNewLoadCanStartWhileTheOtherIsInReview() {
        IntakeBatch next = start.execute(admin, company, IntakeChannel.COMMAND);

        assertThat(next.state()).isEqualTo(IntakeState.OPEN);
        assertThat(listBatches.execute(admin, company)).extracting(IntakeBatch::id)
                .containsExactlyInAnyOrder(batch, next.id());
        assertThat(intake.findActive(company)).map(IntakeBatch::id).contains(next.id());
    }
}
