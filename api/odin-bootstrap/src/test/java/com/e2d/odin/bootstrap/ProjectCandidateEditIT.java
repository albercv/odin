package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.adapter.postgres.PostgresIntakeRepository;
import com.e2d.odin.adapter.postgres.PostgresOrganizationRepository;
import com.e2d.odin.adapter.postgres.PostgresUserRepository;
import com.e2d.odin.adapter.postgres.ScopedTransactions;
import com.e2d.odin.application.intake.EditProjectCandidates;
import com.e2d.odin.application.intake.ListProjectCandidates;
import com.e2d.odin.application.port.DocumentRepository;
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
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.UserId;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Renombrar y juntar candidatos con Postgres de verdad: alias guardados, 409, todo o nada y sin resucitar lo juntado. */
@Testcontainers
@Timeout(60) // un bloqueo entre la transacción externa y la de ScopedTransactions falla pronto, no cuelga
class ProjectCandidateEditIT {

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

    private ProjectCandidate candidate(String name, int documents, DecisionState state) {
        return new ProjectCandidate(company, batch, name, CandidateOrigin.DISCOVERED, documents, state, Optional.empty());
    }

    private EditProjectCandidates edit(PostgresIntakeRepository repo) {
        return new UseCaseConfiguration().editProjectCandidates(repo, organizations, transactionManager);
    }

    @Test
    void renamingJoiningAndTheConflictsWorkWithTheStoredAliases() {
        intake.save(candidate("C/ Olmo 24", 2, DecisionState.PROPOSED));
        intake.save(candidate("Calle Olmo 24 B", 3, DecisionState.PROPOSED));
        intake.save(candidate("Calle Haya 2", 1, DecisionState.REJECTED));
        EditProjectCandidates edit = edit(intake);

        edit.rename(admin, company, batch, "C/ Olmo 24", "Calle Olmo 24");
        List<ProjectCandidate> joined = edit.merge(admin, company, batch, List.of("Calle Olmo 24 B"), "Calle Olmo 24");

        assertThat(joined).extracting(ProjectCandidate::name).containsExactly("Calle Haya 2", "Calle Olmo 24");
        ProjectCandidate olmo = joined.get(1);
        assertThat(olmo.documentCount()).isEqualTo(5);
        assertThat(olmo.aliases()).containsExactlyInAnyOrder("c olmo 24", "calle olmo 24 b");
        assertThatThrownBy(() -> edit.rename(admin, company, batch, "Calle Olmo 24", "calle haya 2"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> edit.rename(admin, company, batch, "Calle Haya 2", "Otra"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aFailureSavingTheJoinedCandidateKeepsEveryoneAsTheyWere() {
        intake.save(candidate("Calle Olmo 24", 1, DecisionState.PROPOSED));
        intake.save(candidate("Residencial Olmos", 1, DecisionState.PROPOSED));
        PostgresIntakeRepository failing = new PostgresIntakeRepository(tx) {
            @Override
            public void save(ProjectCandidate c) {
                throw new IllegalStateException("fallo forzado");
            }
        };

        assertThatThrownBy(() -> edit(failing).merge(admin, company, batch,
                List.of("Residencial Olmos"), "Calle Olmo 24")).isInstanceOf(IllegalStateException.class);

        assertThat(intake.candidates(company, batch)).extracting(ProjectCandidate::name)
                .containsExactly("Calle Olmo 24", "Residencial Olmos");
    }

    @Test
    void discoveryDoesNotBringBackAJoinedName() {
        intake.save(candidate("Calle Olmo 24", 1, DecisionState.PROPOSED));
        intake.save(candidate("Residencial Olmos", 1, DecisionState.PROPOSED));
        edit(intake).merge(admin, company, batch, List.of("Residencial Olmos"),
                "Calle Olmo 24");
        DocumentRepository texts = (DocumentRepository) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {DocumentRepository.class},
                (proxy, method, args) -> List.of("Comunidad: Residencial Olmos\nComunidad: Calle Olmo 24"));

        List<ProjectCandidate> found = new ListProjectCandidates(intake, texts).discover(admin, company, batch);

        assertThat(found).singleElement().satisfies(c -> {
            assertThat(c.name()).isEqualTo("Calle Olmo 24");
            assertThat(c.aliases()).containsExactly("residencial olmos");
        });
    }
}
