package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.adapter.postgres.PostgresOrganizationRepository;
import com.e2d.odin.adapter.postgres.PostgresUserRepository;
import com.e2d.odin.adapter.postgres.ScopedTransactions;
import com.e2d.odin.application.identity.SetUpInstallation;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UserId;
import java.util.Map;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SetUpAtomicityIT {

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");

    @Test
    void failureSavingTheAdminLeavesNoGroup() {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .placeholders(Map.of("appPassword", "app-test-password")).load().migrate();
        DriverManagerDataSource app = new DriverManagerDataSource(PG.getJdbcUrl(), "odin_app", "app-test-password");
        ScopedTransactions tx = new ScopedTransactions(app);
        PostgresOrganizationRepository organizations = new PostgresOrganizationRepository(tx);
        UserRepository failingUsers = new PostgresUserRepository(tx) {
            @Override
            public void save(User user) {
                throw new IllegalStateException("fallo forzado");
            }

            @Override
            public Optional<User> findById(UserId id) {
                return Optional.empty();
            }
        };
        SetUpInstallation setUp = new UseCaseConfiguration().setUpInstallation(organizations, failingUsers,
                new BcryptPasswordHasher(), new RandomUuidGenerator(), new SecureRandomPersonCodeGenerator(),
                new InstallationSettings("token"), new DataSourceTransactionManager(app));

        assertThatThrownBy(() -> setUp.execute("token", "Grupo", "Admin", "a@e.test", "clave-muy-larga-1"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(organizations.findGroup()).isEmpty();
    }
}
