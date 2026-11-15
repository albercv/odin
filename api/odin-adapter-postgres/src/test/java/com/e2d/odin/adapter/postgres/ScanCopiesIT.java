package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** La «copia escaneada» (D3) en la base: quién ve las copias de un documento, y deshacer el enlace. */
class ScanCopiesIT extends AbstractPostgresIT {

    private CompanyId company;
    private UserId user;
    private Document main;
    private Document scan;

    @BeforeEach
    void seed() {
        company = newCompany();
        user = newUser(company);
        main = newDocument(company, user, "Digital", DocumentStatus.READY);
        scan = newDocument(company, user, "Escaneo", DocumentStatus.READY);
        docs.markScanOf(company, scan.id(), main.id());
    }

    private static void owner(String sql) {
        JdbcClient.create(Db.owner()).sql(sql).update();
    }

    private List<DocumentRef> copies(CompanyScope scope, ProjectVisibility visibility, Optional<DocumentStatus> status) {
        return docs.scanCopiesOf(scope, visibility, status, List.of(main.id())).getOrDefault(main.id(), List.of());
    }

    @Test
    void theMainSeesItsScanAndTheScanSeesItsMain() {
        CompanyScope scope = CompanyScope.of(company);

        assertThat(copies(scope, ProjectVisibility.all(), Optional.empty())).containsExactly(DocumentRef.of(scan));
        assertThat(docs.refsOf(scope, ProjectVisibility.all(), List.of(main.id())))
                .containsEntry(main.id(), DocumentRef.of(main));
    }

    @Test
    void aPendingOrWithdrawnScanIsNotShown() {
        CompanyScope scope = CompanyScope.of(company);
        IntakeBatch batch = IntakeBatch.open(new IntakeBatchId(UUID.randomUUID()), company, user, IntakeChannel.PANEL, NOW);
        new PostgresIntakeRepository(tx).save(batch);
        owner("UPDATE document SET intake_pending = true, intake_batch_id = '" + batch.id().value() + "' WHERE id = '"
                + scan.id().value() + "'");
        assertThat(copies(scope, ProjectVisibility.all(), Optional.empty())).isEmpty();

        owner("UPDATE document SET intake_pending = false, withdrawn_at = now(), withdrawn_by = '"
                + user.value() + "' WHERE id = '" + scan.id().value() + "'");
        assertThat(copies(scope, ProjectVisibility.all(), Optional.empty())).isEmpty();
    }

    @Test
    void theStatusFilterHidesScansStillBeingRead() {
        owner("UPDATE document SET status = 'PROCESSING' WHERE id = '" + scan.id().value() + "'");

        assertThat(copies(CompanyScope.of(company), ProjectVisibility.all(), Optional.of(DocumentStatus.READY))).isEmpty();
        assertThat(copies(CompanyScope.of(company), ProjectVisibility.all(), Optional.empty())).hasSize(1);
    }

    @Test
    void aWorkerOfAnotherProjectOrCompanyDoesNotSeeTheCopies() {
        ProjectId secret = newProject(company, "Secreto");
        ProjectId other = newProject(company, "Otro");
        owner("UPDATE document SET project_id = '" + secret.value() + "' WHERE id = '" + scan.id().value() + "'");

        assertThat(copies(CompanyScope.of(company), ProjectVisibility.of(Set.of(other)), Optional.empty())).isEmpty();
        assertThat(copies(CompanyScope.of(company), ProjectVisibility.of(Set.of(secret)), Optional.empty())).hasSize(1);
        assertThat(copies(CompanyScope.of(newCompany()), ProjectVisibility.all(), Optional.empty())).isEmpty();
        assertThat(docs.refsOf(CompanyScope.of(newCompany()), ProjectVisibility.all(), List.of(main.id()))).isEmpty();
    }

    @Test
    void unlinkingPutsTheScanBackInTheListings() {
        CompanyScope scope = CompanyScope.of(company);
        assertThat(docs.list(scope, ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 10))
                .extracting(e -> e.document().id()).containsExactly(main.id());

        docs.clearScanOf(company, scan.id());

        assertThat(docs.list(scope, ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 10))
                .extracting(e -> e.document().id()).containsExactlyInAnyOrder(main.id(), scan.id());
        assertThat(copies(scope, ProjectVisibility.all(), Optional.empty())).isEmpty();
    }

    @Test
    void unlinkingInAnotherCompanyIsNotFound() {
        assertThatThrownBy(() -> docs.clearScanOf(newCompany(), scan.id())).isInstanceOf(NotFoundException.class);
        assertThat(docs.findById(CompanyScope.of(company), ProjectVisibility.all(), scan.id()).orElseThrow().scanOf())
                .contains(main.id());
    }
}
