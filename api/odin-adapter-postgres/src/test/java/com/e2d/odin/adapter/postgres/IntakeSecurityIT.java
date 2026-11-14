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
import com.e2d.odin.domain.intake.IntakeProposal;
import com.e2d.odin.domain.intake.ProposalConfidence;
import com.e2d.odin.domain.intake.ProposalKind;
import com.e2d.odin.domain.intake.ProposalSource;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
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
}
