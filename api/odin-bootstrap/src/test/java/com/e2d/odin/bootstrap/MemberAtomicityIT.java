package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.adapter.postgres.PostgresAccessKeyRepository;
import com.e2d.odin.adapter.postgres.PostgresMembershipRepository;
import com.e2d.odin.adapter.postgres.PostgresOrganizationRepository;
import com.e2d.odin.adapter.postgres.PostgresUserRepository;
import com.e2d.odin.adapter.postgres.ScopedTransactions;
import com.e2d.odin.application.identity.AddMember;
import com.e2d.odin.application.identity.DeleteUser;
import com.e2d.odin.application.identity.MemberChanges;
import com.e2d.odin.application.identity.NewMember;
import com.e2d.odin.application.identity.UpdateMember;
import com.e2d.odin.application.identity.RemoveMember;
import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Las escrituras que tocan a la persona y a su pertenencia a la vez se confirman juntas o no se confirman. */
@Testcontainers
class MemberAtomicityIT {

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");

    private static final Actor GROUP_ADMIN = new Actor(new UserId(UUID.randomUUID()), "Gema", Role.GROUP_ADMIN,
            Set.of());

    private static final AdminSessions NO_SESSIONS = new AdminSessions() {
        @Override
        public String open(UserId userId) {
            return "";
        }

        @Override
        public Optional<UserId> resolve(String token) {
            return Optional.empty();
        }

        @Override
        public void close(String token) {
        }

        @Override
        public void closeAllOf(UserId userId) {
        }
    };

    private static ScopedTransactions tx;
    private static DataSourceTransactionManager transactionManager;
    private static PostgresUserRepository users;
    private static PostgresMembershipRepository memberships;
    private static PostgresOrganizationRepository organizations;
    private final CompanyId company = new CompanyId(UUID.randomUUID());

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .placeholders(Map.of("appPassword", "app-test-password")).load().migrate();
        DriverManagerDataSource app = new DriverManagerDataSource(PG.getJdbcUrl(), "odin_app", "app-test-password");
        tx = new ScopedTransactions(app);
        transactionManager = new DataSourceTransactionManager(app);
        users = new PostgresUserRepository(tx);
        memberships = new PostgresMembershipRepository(tx);
        organizations = new PostgresOrganizationRepository(tx);
    }

    private static MembershipRepository failingMemberships() {
        return new PostgresMembershipRepository(tx) {
            @Override
            public void save(Membership membership) {
                throw new IllegalStateException("fallo forzado");
            }
        };
    }

    private static UserRepository failingUserDelete() {
        return new PostgresUserRepository(tx) {
            @Override
            public void delete(UserId id, Instant at) {
                throw new IllegalStateException("fallo forzado");
            }
        };
    }

    private static AccessKeyRepository failingKeySave() {
        return new PostgresAccessKeyRepository(tx) {
            @Override
            public void save(AccessKey key) {
                throw new IllegalStateException("fallo forzado");
            }
        };
    }

    private void givenCompany() {
        organizations.saveCompany(new Company(company, "Empresa", EnumSet.allOf(Capability.class)));
    }

    @Test
    void failureSavingTheMembershipLeavesNoPerson() {
        givenCompany();
        AddMember add = new UseCaseConfiguration().addMember(users, failingMemberships(), organizations,
                new BcryptPasswordHasher(), new RandomUuidGenerator(), transactionManager);

        assertThatThrownBy(() -> add.execute(GROUP_ADMIN, company,
                new NewMember("Wanda", Optional.of("atomica@e.test"), Set.of(Role.WORKER), Optional.empty())))
                .isInstanceOf(IllegalStateException.class);

        assertThat(users.findByEmail("atomica@e.test")).isEmpty();
    }

    @Test
    void failureSavingTheMembershipLeavesNoPasswordOnAnExistingPerson() {
        givenCompany();
        UserId id = new UserId(UUID.randomUUID());
        users.save(new User(id, "Wanda", Optional.of("sinclave@e.test"), false, true, Optional.empty()));
        memberships.save(new Membership(id, company, Set.of(Role.WORKER), true));
        UpdateMember update = new UseCaseConfiguration().updateMember(users, failingMemberships(),
                new BcryptPasswordHasher(), NO_SESSIONS, transactionManager);

        assertThatThrownBy(() -> update.execute(GROUP_ADMIN, company, id, new MemberChanges(
                Optional.of(Set.of(Role.COMPANY_ADMIN)), Optional.empty(), Optional.of("clave-muy-larga-1"))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(users.findById(id).orElseThrow().passwordHash()).isEmpty();
    }

    @Test
    void failureDeletingThePersonKeepsTheirMemberships() {
        givenCompany();
        UserId id = new UserId(UUID.randomUUID());
        users.save(new User(id, "Wanda", Optional.of("borrar@e.test"), false, true, Optional.empty()));
        memberships.save(new Membership(id, company, Set.of(Role.WORKER), true));
        DeleteUser delete = new UseCaseConfiguration().deleteUser(failingUserDelete(), memberships,
                NO_SESSIONS, Clock.systemUTC(), transactionManager);

        assertThatThrownBy(() -> delete.execute(GROUP_ADMIN, id)).isInstanceOf(IllegalStateException.class);

        assertThat(memberships.find(id, company)).isPresent();
    }

    @Test
    void failureRevokingKeysKeepsTheMembership() {
        givenCompany();
        UserId id = new UserId(UUID.randomUUID());
        users.save(new User(id, "Wanda", Optional.empty(), false, true, Optional.empty()));
        memberships.save(new Membership(id, company, Set.of(Role.WORKER), true));
        new PostgresAccessKeyRepository(tx).save(new AccessKey(new AccessKeyId(UUID.randomUUID()), id, company,
                "portatil", "atomic01", "hash", Instant.now(), Optional.empty()));
        RemoveMember remove = new UseCaseConfiguration().removeMember(memberships, failingKeySave(), NO_SESSIONS,
                Clock.systemUTC(), transactionManager);

        assertThatThrownBy(() -> remove.execute(GROUP_ADMIN, company, id)).isInstanceOf(IllegalStateException.class);

        assertThat(memberships.find(id, company)).isPresent();
    }

    @Test
    void failureDeletingTheMembershipKeepsItsKeysValid() {
        givenCompany();
        UserId id = new UserId(UUID.randomUUID());
        users.save(new User(id, "Wanda", Optional.empty(), false, true, Optional.empty()));
        memberships.save(new Membership(id, company, Set.of(Role.WORKER), true));
        PostgresAccessKeyRepository keys = new PostgresAccessKeyRepository(tx);
        keys.save(new AccessKey(new AccessKeyId(UUID.randomUUID()), id, company, "portatil", "atomic02", "hash",
                Instant.now(), Optional.empty()));
        MembershipRepository failingDelete = new PostgresMembershipRepository(tx) {
            @Override
            public void delete(UserId userId, CompanyId companyId, Instant at) {
                throw new IllegalStateException("fallo forzado");
            }
        };
        RemoveMember remove = new UseCaseConfiguration().removeMember(failingDelete, keys, NO_SESSIONS,
                Clock.systemUTC(), transactionManager);

        assertThatThrownBy(() -> remove.execute(GROUP_ADMIN, company, id)).isInstanceOf(IllegalStateException.class);

        assertThat(keys.listByMembership(id, company)).allSatisfy(k -> assertThat(k.revokedAt()).isEmpty());
    }
}
