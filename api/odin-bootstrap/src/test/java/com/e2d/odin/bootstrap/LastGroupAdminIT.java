package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.adapter.postgres.PostgresMembershipRepository;
import com.e2d.odin.adapter.postgres.PostgresProjectMemberRepository;
import com.e2d.odin.adapter.postgres.PostgresUserRepository;
import com.e2d.odin.adapter.postgres.ScopedTransactions;
import com.e2d.odin.application.identity.UserChanges;
import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Dos administradores del grupo que se quitan el acceso a la vez no pueden dejar el grupo sin ninguno activo. */
@Testcontainers
class LastGroupAdminIT {

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.10");

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
    private static PostgresUserRepository plainUsers;
    private static PostgresMembershipRepository memberships;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .placeholders(Map.of("appPassword", "app-test-password")).load().migrate();
        DriverManagerDataSource app = new DriverManagerDataSource(PG.getJdbcUrl(), "odin_app", "app-test-password");
        tx = new ScopedTransactions(app);
        transactionManager = new DataSourceTransactionManager(app);
        plainUsers = new PostgresUserRepository(tx);
        memberships = new PostgresMembershipRepository(tx);
    }

    /** Ambos hilos llegan a la comprobación antes de que cualquiera confirme; con el bloqueo el segundo espera. */
    private static UserRepository meetingAtTheCheck(CountDownLatch bothChecking) {
        return new PostgresUserRepository(tx) {
            @Override
            public List<User> lockGroupAdmins() {
                List<User> admins = super.lockGroupAdmins();
                bothChecking.countDown();
                await(bothChecking);
                return admins;
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @BeforeEach
    void startWithNoGroupAdmins() {
        plainUsers.listGroupAdmins().forEach(admin -> plainUsers.delete(admin.id(), Instant.now()));
    }

    private static User groupAdmin(String name) {
        User user = new User(new UserId(UUID.randomUUID()), name, Optional.of("Gil"),
                new SecureRandomPersonCodeGenerator().next(), Optional.of(name + "@e.test"), true, true,
                Optional.of("hash"));
        plainUsers.save(user);
        return user;
    }

    private static Actor actingAs(User user) {
        return new Actor(user.id(), user.displayName(), Role.GROUP_ADMIN, Set.of());
    }

    private static void deactivateEachOther(User first, User second, UserRepository users) throws Exception {
        UseCaseConfiguration config = new UseCaseConfiguration();
        var update = config.updateUser(users, memberships, NO_SESSIONS, transactionManager);
        UserChanges off = new UserChanges(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(false));
        runBoth(() -> update.execute(actingAs(first), second.id(), off),
                () -> update.execute(actingAs(second), first.id(), off));
    }

    private static void deleteEachOther(User first, User second, UserRepository users) throws Exception {
        var delete = new UseCaseConfiguration().deleteUser(users, memberships,
                new PostgresProjectMemberRepository(tx), NO_SESSIONS, Clock.systemUTC(), transactionManager);
        runBoth(() -> {
            delete.execute(actingAs(first), second.id());
            return null;
        }, () -> {
            delete.execute(actingAs(second), first.id());
            return null;
        });
    }

    private static void runBoth(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> results = List.of(pool.submit(first), pool.submit(second));
            List<Throwable> failures = new ArrayList<>();
            for (Future<?> result : results) {
                try {
                    result.get(20, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0)).isInstanceOf(ConflictException.class)
                    .hasMessage("Tiene que quedar al menos un administrador del grupo activo");
        } finally {
            pool.shutdownNow();
        }
    }

    private static long activeOf(User first, User second) {
        return List.of(first.id(), second.id()).stream().map(id -> plainUsers.findById(id))
                .filter(found -> found.map(User::active).orElse(false)).count();
    }

    @Test
    void twoGroupAdminsDeactivatingEachOtherLeaveOneActive() throws Exception {
        User first = groupAdmin("ana");
        User second = groupAdmin("beto");

        deactivateEachOther(first, second, meetingAtTheCheck(new CountDownLatch(2)));

        assertThat(activeOf(first, second)).isEqualTo(1);
    }

    @Test
    void twoGroupAdminsDeletingEachOtherLeaveOneActive() throws Exception {
        User first = groupAdmin("carla");
        User second = groupAdmin("dani");

        deleteEachOther(first, second, meetingAtTheCheck(new CountDownLatch(2)));

        assertThat(activeOf(first, second)).isEqualTo(1);
    }
}
