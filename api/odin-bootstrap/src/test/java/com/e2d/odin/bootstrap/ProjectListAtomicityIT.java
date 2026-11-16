package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.adapter.postgres.PostgresIntakeRepository;
import com.e2d.odin.adapter.postgres.PostgresOrganizationRepository;
import com.e2d.odin.adapter.postgres.PostgresUserRepository;
import com.e2d.odin.adapter.postgres.ScopedTransactions;
import com.e2d.odin.application.intake.ConfirmProjectCandidates;
import com.e2d.odin.application.intake.ImportProjectList;
import com.e2d.odin.application.intake.ProjectDecision.Action;
import com.e2d.odin.application.intake.ProjectDecision;
import com.e2d.odin.application.organization.CreateProject;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.intake.CandidateOrigin;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.ProjectCandidate;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Confirmar e importar la lista de proyectos son todo o nada: un fallo a mitad no deja nada cambiado. */
@Testcontainers
@Timeout(60) // un bloqueo entre la transacción externa y la de ScopedTransactions falla pronto, no cuelga
class ProjectListAtomicityIT {

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");

    private static ScopedTransactions tx;
    private static DataSourceTransactionManager transactionManager;
    private static PostgresOrganizationRepository organizations;
    private static PostgresIntakeRepository intake;
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private Actor admin;
    private IntakeBatchId batch;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .placeholders(Map.of("appPassword", "app-test-password")).load().migrate();
        DriverManagerDataSource app = new DriverManagerDataSource(PG.getJdbcUrl(), "odin_app", "app-test-password");
        tx = new ScopedTransactions(app);
        transactionManager = new DataSourceTransactionManager(app);
        organizations = new PostgresOrganizationRepository(tx);
        intake = new PostgresIntakeRepository(tx);
    }

    @BeforeEach
    void seed() {
        organizations.saveCompany(new Company(company, "Empresa", new SecureRandomPersonCodeGenerator().next(),
                EnumSet.allOf(Capability.class)));
        UserId user = new UserId(UUID.randomUUID());
        new PostgresUserRepository(tx).save(new User(user, "Ada", Optional.of("Pérez"),
                new SecureRandomPersonCodeGenerator().next(), Optional.empty(), false, true, Optional.empty()));
        admin = new Actor(user, "Ada", Role.COMPANY_ADMIN, Set.of(company));
        batch = new IntakeBatchId(UUID.randomUUID());
        intake.save(IntakeBatch.open(batch, company, user, IntakeChannel.PANEL, Instant.now()));
    }

    private ProjectCandidate candidate(String name, CandidateOrigin origin) {
        return new ProjectCandidate(company, batch, name, origin, 0, DecisionState.PROPOSED, Optional.empty());
    }

    @Test
    void aFailureCreatingTheSecondProjectLeavesNoProjectAndNoCandidateDecided() {
        intake.save(candidate("Calle Olmo 24", CandidateOrigin.IMPORTED));
        intake.save(candidate("Calle Haya 2", CandidateOrigin.IMPORTED));
        AtomicInteger saved = new AtomicInteger();
        PostgresOrganizationRepository failingSecond = new PostgresOrganizationRepository(tx) {
            @Override
            public void saveProject(Project project) {
                if (saved.incrementAndGet() == 2) {
                    throw new IllegalStateException("fallo forzado");
                }
                super.saveProject(project);
            }
        };
        ConfirmProjectCandidates confirm = new UseCaseConfiguration().confirmProjectCandidates(intake, failingSecond,
                new CreateProject(failingSecond, new RandomUuidGenerator(), new NoAudit()), transactionManager, new NoAudit());

        assertThatThrownBy(() -> confirm.execute(admin, company, batch, List.of(
                new ProjectDecision("Calle Olmo 24", Action.CREATE, Optional.empty()),
                new ProjectDecision("Calle Haya 2", Action.CREATE, Optional.empty()))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(organizations.listProjects(company)).isEmpty();
        assertThat(intake.candidates(company, batch)).extracting(ProjectCandidate::state)
                .containsOnly(DecisionState.PROPOSED);
    }

    @Test
    void aFailureSavingAnImportedNameKeepsTheDiscoveredCandidatesAsTheyWere() {
        intake.save(candidate("Calle Olmo 24", CandidateOrigin.DISCOVERED));
        PostgresIntakeRepository failing = new PostgresIntakeRepository(tx) {
            @Override
            public void save(ProjectCandidate c) {
                if (c.origin() == CandidateOrigin.IMPORTED) {
                    throw new IllegalStateException("fallo forzado");
                }
                super.save(c);
            }
        };
        ImportProjectList importList = new UseCaseConfiguration().importProjectList(failing, transactionManager, new NoAudit());

        assertThatThrownBy(() -> importList.execute(admin, company, batch, "Calle Haya 2".getBytes()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(intake.candidates(company, batch)).singleElement().satisfies(c -> {
            assertThat(c.name()).isEqualTo("Calle Olmo 24");
            assertThat(c.state()).isEqualTo(DecisionState.PROPOSED);
        });
    }
}
