package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.CandidateOrigin;
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.ProjectCandidate;
import com.e2d.odin.domain.intake.ProposalConfidence;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.intake.ProposalSource;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** D1 en la base (RLS con el rol de la aplicación, sin filtro propio) y las restricciones del modelo de la primera carga. */
class IntakeSecurityIT extends AbstractPostgresIT {

    private final PostgresIntakeRepository intake = new PostgresIntakeRepository(tx);
    private final PostgresDocumentRepository ownerDocs = new PostgresDocumentRepository(new ScopedTransactions(Db.owner()));
    private CompanyId company;
    private UserId user;
    private IntakeBatch batch;
    private Document pending;

    @BeforeEach
    void seed() {
        company = newCompany();
        user = newUser(company);
        batch = IntakeBatch.open(new IntakeBatchId(UUID.randomUUID()), company, user, IntakeChannel.PANEL, NOW);
        intake.save(batch);
        pending = newDocument(company, user, "Pendiente", DocumentStatus.READY);
        docs.replacePages(pending.id(), List.of(new DocumentPage(pending.id(), 1, "texto", "lector", "1")));
        owner("UPDATE document SET intake_pending = true, intake_batch_id = '" + batch.id().value() + "' WHERE id = '"
                + pending.id().value() + "'");
    }

    private static void owner(String sql) {
        JdbcClient.create(Db.owner()).sql(sql).update();
    }

    /** Cuenta filas con el rol de la aplicación y las variables de sesión dadas, sin ningún filtro propio. */
    private int count(String table, String includeIntake) {
        return new TransactionTemplate(new DataSourceTransactionManager(Db.app())).execute(status -> {
            JdbcClient jdbc = JdbcClient.create(Db.app());
            jdbc.sql("SELECT set_config('app.company_ids', :c, true), set_config('app.group_scope', 'off', true), "
                    + "set_config('app.all_projects', 'on', true), set_config('app.include_intake', :i, true)")
                    .param("c", company.value().toString()).param("i", includeIntake).query().singleRow();
            return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
        });
    }

    @Test
    void pendingDocumentAndItsPagesAreHiddenFromTheRlsUnlessIncluded() {
        assertThat(count("document", "off")).isZero();
        assertThat(count("document_page", "off")).isZero();
        assertThat(count("document", "on")).isEqualTo(1);
        assertThat(count("document_page", "on")).isEqualTo(1);
    }

    @Test
    void batchesAndProposalsAreOnlyForTheTray() {
        intake.save(new IntakeProposal(UUID.randomUUID(), company, batch.id(), pending.id(), ProposalKind.COMPANY_WIDE,
                Optional.empty(), Optional.empty(), Optional.empty(), ProposalConfidence.LOW, ProposalSource.MODEL,
                "cita", DecisionState.PROPOSED, Optional.empty(), Optional.empty()));
        assertThat(count("intake_batch", "off")).isZero();
        assertThat(count("intake_proposal", "off")).isZero();
        assertThat(count("intake_batch", "on")).isEqualTo(1);
        assertThat(count("intake_proposal", "on")).isEqualTo(1);
    }

    @Test
    void openBatchesAreTheNotClosedOnesOfTheCompanyMostRecentFirst() {
        intake.save(batch.startReading().finishReading());
        IntakeBatch newer = IntakeBatch.open(new IntakeBatchId(UUID.randomUUID()), company, user, IntakeChannel.COMMAND,
                NOW.plusSeconds(60));
        intake.save(newer);
        IntakeBatch closed = IntakeBatch.open(new IntakeBatchId(UUID.randomUUID()), company, user, IntakeChannel.COMMAND,
                NOW.minusSeconds(60)).startReading().finishReading().close(NOW);
        intake.save(closed);
        CompanyId other = newCompany();
        intake.save(IntakeBatch.open(new IntakeBatchId(UUID.randomUUID()), other, newUser(other), IntakeChannel.COMMAND, NOW));

        assertThat(intake.findOpen(company)).extracting(IntakeBatch::id).containsExactly(newer.id(), batch.id());
    }

    @Test
    void titlesOfNeverResolveADocumentOfAnotherCompany() {
        CompanyId other = newCompany();
        Document foreign = newDocument(other, newUser(other), "Ajeno", DocumentStatus.READY);
        Document own = newDocument(company, user, "Propio", DocumentStatus.READY);

        assertThat(docs.titlesOf(company, List.of(own.id(), foreign.id()))).containsOnlyKeys(own.id())
                .containsEntry(own.id(), "Propio");
    }

    /** D1: el texto de un documento pendiente solo sale por la ruta de la lista de proyectos, y de su empresa y lote. */
    @Test
    void projectCandidatesAreOnlyForTheTrayAndTheirTextsComeOnlyFromTheOwnBatch() {
        docs.replacePages(pending.id(), List.of(new DocumentPage(pending.id(), 1, "uno", "lector", "1"),
                new DocumentPage(pending.id(), 2, "dos", "lector", "1")));
        CompanyId other = newCompany();
        intake.save(new ProjectCandidate(company, batch.id(), "Calle Haya 2", CandidateOrigin.DISCOVERED, 1,
                DecisionState.PROPOSED, Optional.empty()));

        assertThat(count("intake_project_candidate", "off")).isZero();
        assertThat(count("intake_project_candidate", "on")).isEqualTo(1);
        assertThat(intake.candidates(company, batch.id())).extracting(ProjectCandidate::name).containsExactly("Calle Haya 2");
        assertThat(intake.candidates(other, batch.id())).isEmpty();
        assertThat(docs.intakeTexts(company, batch.id())).containsExactly("uno\ndos");
        assertThat(docs.intakeTexts(other, batch.id())).isEmpty();
        assertThat(docs.intakeTexts(company, new IntakeBatchId(UUID.randomUUID()))).isEmpty();
    }

    /** D1 y D-014: los documentos de la clasificación salen solo de su empresa y lote, y con el lector que los leyó. */
    @Test
    void classificationReadsOnlyReadyPendingDocumentsOfItsOwnBatchAndCompany() {
        docs.replacePages(pending.id(), List.of(new DocumentPage(pending.id(), 1, "uno", "unlimited-ocr-2", "1"),
                new DocumentPage(pending.id(), 2, "dos", "unlimited-ocr-2", "1")));
        Document unread = newDocument(company, user, "Sin leer", DocumentStatus.PENDING_READER);
        owner("UPDATE document SET intake_pending = true, intake_batch_id = '" + batch.id().value() + "' WHERE id = '"
                + unread.id().value() + "'");
        CompanyId other = newCompany();

        assertThat(docs.intakeReadyDocuments(company, batch.id())).singleElement().satisfies(d -> {
            assertThat(d.id()).isEqualTo(pending.id());
            assertThat(d.text()).isEqualTo("uno\ndos");
            assertThat(d.scan()).isTrue();
        });
        assertThat(docs.intakeReadyDocuments(other, batch.id())).isEmpty();
        assertThat(docs.readerQueueBusy(company)).isTrue();
        assertThat(docs.readerQueueBusy(other)).isFalse();
        assertThat(intake.findReading()).isEmpty();
        intake.save(batch.startReading());
        assertThat(intake.findReading()).extracting(IntakeBatch::id).containsExactly(batch.id());
    }

    @Test
    void sqlFilterHidesPendingAndScansEvenWithoutRls() {
        Document digital = newDocument(company, user, "Digital", DocumentStatus.READY);
        Document scan = newDocument(company, user, "Escaneo", DocumentStatus.READY);
        owner("UPDATE document SET scan_of = '" + digital.id().value() + "' WHERE id = '" + scan.id().value() + "'");
        CompanyScope scope = CompanyScope.of(company);

        var listed = ownerDocs.list(scope, ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 10);

        assertThat(listed).extracting(e -> e.document().id()).containsExactly(digital.id());
        assertThat(ownerDocs.findEntry(scope, ProjectVisibility.all(), pending.id())).isEmpty();
        assertThat(ownerDocs.findPage(scope, ProjectVisibility.all(), pending.id(), 1)).isEmpty();
        assertThat(ownerDocs.findEntry(scope, ProjectVisibility.all(), scan.id())).isPresent();
    }

    @Test
    void secondActiveBatchInTheSameCompanyFails() {
        IntakeBatch second = IntakeBatch.open(new IntakeBatchId(UUID.randomUUID()), company, user, IntakeChannel.COMMAND, NOW);
        assertThatThrownBy(() -> intake.save(second)).isInstanceOf(ConflictException.class);

        intake.save(batch.startReading());
        assertThatThrownBy(() -> intake.save(second)).isInstanceOf(ConflictException.class);

        intake.save(batch.startReading().finishReading());
        intake.save(second);
        assertThat(intake.findActive(company)).map(IntakeBatch::id).contains(second.id());
    }

    @Test
    void anotherCompanyMayHaveItsOwnActiveBatch() {
        CompanyId other = newCompany();
        intake.save(IntakeBatch.open(new IntakeBatchId(UUID.randomUUID()), other, newUser(other), IntakeChannel.PANEL, NOW));
        assertThat(intake.findActive(other)).isPresent();
        assertThat(intake.find(other, batch.id())).isEmpty();
    }

    @Test
    void scanOfAnotherCompanyIsRejectedByTheCompositeForeignKey() {
        CompanyId other = newCompany();
        Document foreign = newDocument(other, newUser(other), "Ajeno", DocumentStatus.READY);
        Document mine = newDocument(company, user, "Mío", DocumentStatus.READY);

        assertThatThrownBy(() -> owner("UPDATE document SET scan_of = '" + foreign.id().value() + "' WHERE id = '"
                + mine.id().value() + "'")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void batchOfAnotherCompanyIsRejectedByTheCompositeForeignKey() {
        CompanyId other = newCompany();
        Document foreign = newDocument(other, newUser(other), "Ajeno", DocumentStatus.READY);

        assertThatThrownBy(() -> owner("UPDATE document SET intake_batch_id = '" + batch.id().value()
                + "' WHERE id = '" + foreign.id().value() + "'")).isInstanceOf(DataIntegrityViolationException.class);
    }

    /** La RLS de la propuesta o la clave foránea compuesta, lo que salte antes. */
    @Test
    void proposalWithADocumentOfAnotherCompanyIsRejected() {
        CompanyId other = newCompany();
        Document foreign = newDocument(other, newUser(other), "Ajeno", DocumentStatus.READY);

        assertThatThrownBy(() -> intake.save(new IntakeProposal(UUID.randomUUID(), company, batch.id(), foreign.id(),
                ProposalKind.COMPANY_WIDE, Optional.empty(), Optional.empty(), Optional.empty(),
                ProposalConfidence.LOW, ProposalSource.MODEL, "cita", DecisionState.PROPOSED, Optional.empty(),
                Optional.empty()))).isInstanceOf(DataAccessException.class);
    }

    @Test
    void guardOfTheProjectConstraintIsIdempotent() throws Exception {
        String migration = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/db/migration/V26__intake.sql"));
        var m = java.util.regex.Pattern.compile("DO \\$\\$.*?END \\$\\$;", java.util.regex.Pattern.DOTALL).matcher(migration);
        assertThat(m.find()).isTrue();

        // La restricción ya existe (la creó la V25 o el otro encargo): volver a aplicar la guarda no falla ni duplica.
        owner(m.group());
        assertThat(JdbcClient.create(Db.owner()).sql("SELECT count(*) FROM pg_constraint WHERE conname = "
                + "'project_id_company_unique'").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void searchNeverReturnsPendingDocumentsNorLinkedScans() {
        Document digital = searchable("Digital", "baliza");
        Document scan = searchable("Escaneo", "baliza");
        owner("UPDATE document SET scan_of = '" + digital.id().value() + "' WHERE id = '" + scan.id().value() + "'");
        docs.replacePages(pending.id(), List.of(new DocumentPage(pending.id(), 1, "baliza", "lector", "1")));

        assertThat(searchTitles()).containsExactly("Digital");

        owner("UPDATE document SET scan_of = NULL WHERE id = '" + scan.id().value() + "'");
        owner("UPDATE document SET intake_pending = false WHERE id = '" + pending.id().value() + "'");
        assertThat(searchTitles()).containsExactlyInAnyOrder("Digital", "Escaneo", "Pendiente");
    }

    private Document searchable(String title, String text) {
        Document d = newDocument(company, user, title, DocumentStatus.READY);
        docs.replacePages(d.id(), List.of(new DocumentPage(d.id(), 1, text, "lector", "1")));
        return d;
    }

    /** Con la conexión del propietario, sin RLS: solo el filtro SQL de la búsqueda separa lo oculto. */
    private List<String> searchTitles() {
        var search = new PostgresKnowledgeSearch(new ScopedTransactions(Db.owner()));
        return search.search(new com.e2d.odin.domain.search.SearchQuery(List.of("baliza"), CompanyScope.of(company),
                ProjectVisibility.all(), Optional.empty(), 10, user, Optional.empty())).hits().stream()
                .map(h -> h.title()).toList();
    }

    @Test
    void proposalOfADocumentWithAProjectIsListedAndUpdated() {
        ProjectId project = newProject(company, "A");
        owner("UPDATE document SET project_id = '" + project.value() + "' WHERE id = '" + pending.id().value() + "'");
        IntakeProposal proposal = new IntakeProposal(UUID.randomUUID(), company, batch.id(), pending.id(),
                ProposalKind.CASE_MARK, Optional.empty(), Optional.empty(), Optional.of("1/2026"),
                ProposalConfidence.HIGH, ProposalSource.RULES, "cita", DecisionState.PROPOSED, Optional.empty(),
                Optional.empty());
        intake.save(proposal);
        assertThat(intake.proposals(company, batch.id())).containsExactly(proposal);

        IntakeProposal confirmed = proposal.confirm(user, NOW);
        intake.save(confirmed);
        assertThat(intake.proposals(company, batch.id())).containsExactly(confirmed);
    }

    @Test
    void adminListShowsLinkedScansAndWorkerListDoesNot() {
        Document digital = newDocument(company, user, "Digital", DocumentStatus.READY);
        Document scan = newDocument(company, user, "Escaneo", DocumentStatus.READY);
        owner("UPDATE document SET scan_of = '" + digital.id().value() + "' WHERE id = '" + scan.id().value() + "'");
        CompanyScope scope = CompanyScope.of(company);

        assertThat(docs.list(scope, ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 10))
                .extracting(e -> e.document().id()).containsExactly(digital.id());
        for (PostgresDocumentRepository repo : List.of(docs, ownerDocs)) {
            assertThat(adminList(repo, false, Optional.empty())).containsExactly(digital.id(), scan.id());
            assertThat(adminList(repo, false, Optional.of("escaneo"))).containsExactly(scan.id());
            assertThat(repo.count(scope, ProjectVisibility.all(), false, Optional.empty())).isEqualTo(2);
        }
    }

    /** D1 en la lista paginada del administrador, su buscador y su total: con la RLS y con el filtro SQL solo. */
    @Test
    void adminListSearchAndTotalNeverIncludePendingDocuments() {
        Document visible = newDocument(company, user, "Pendiente de pago", DocumentStatus.READY);
        CompanyScope scope = CompanyScope.of(company);

        for (PostgresDocumentRepository repo : List.of(docs, ownerDocs)) {
            assertThat(adminList(repo, false, Optional.empty())).containsExactly(visible.id());
            assertThat(adminList(repo, false, Optional.of("pendiente"))).containsExactly(visible.id());
            assertThat(repo.count(scope, ProjectVisibility.all(), false, Optional.empty())).isEqualTo(1);
            assertThat(repo.count(scope, ProjectVisibility.all(), false, Optional.of("pendiente"))).isEqualTo(1);
        }
    }

    private List<DocumentId> adminList(PostgresDocumentRepository repo, boolean withdrawn,
            Optional<String> title) {
        return repo.search(CompanyScope.of(company), ProjectVisibility.all(), withdrawn, title, 0, 10).stream()
                .map(e -> e.document().id()).toList();
    }

    @Test
    void withdrawnTabNeverListsPendingDocuments() {
        Document withdrawn = newDocument(company, user, "Retirado", DocumentStatus.READY);
        owner("UPDATE document SET withdrawn_at = now(), withdrawn_by = '" + user.value() + "' WHERE id IN ('"
                + withdrawn.id().value() + "', '" + pending.id().value() + "')");
        CompanyScope scope = CompanyScope.of(company);

        for (PostgresDocumentRepository repo : List.of(docs, ownerDocs)) {
            assertThat(adminList(repo, true, Optional.empty())).containsExactly(withdrawn.id());
            assertThat(adminList(repo, true, Optional.of("pendiente"))).isEmpty();
            assertThat(repo.count(scope, ProjectVisibility.all(), true, Optional.empty())).isEqualTo(1);
        }
    }
}
