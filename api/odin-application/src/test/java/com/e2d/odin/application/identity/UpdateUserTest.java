package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateUserTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final StoredAdminSessions sessions = new StoredAdminSessions(new InMemoryAdminSessionRepository(),
            new FakeSecretHasher(), new SequenceTokenGenerator(), new SessionSettings(), new MutableClock());
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final UpdateUser update = new UpdateUser(users, memberships, sessions, audit);
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private final UserId workerId = new UserId(UUID.randomUUID());
    private final UserId adminId = new UserId(UUID.randomUUID());
    private final Actor admin = Actors.companyAdmin(company);

    UpdateUserTest() {
        People.member(users, memberships, workerId, "Wanda", Optional.of("w@demo.test"), Role.WORKER, company, true,
                Optional.empty());
        People.member(users, memberships, adminId, "Carlos", Optional.of("c@demo.test"), Role.COMPANY_ADMIN, company,
                true, Optional.of("pw:x"));
    }

    private static UserChanges name(String name) {
        return new UserChanges(Optional.of(name), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static UserChanges surnames(String surnames) {
        return new UserChanges(Optional.empty(), Optional.of(surnames), Optional.empty(), Optional.empty());
    }

    private static UserChanges email(String email) {
        return new UserChanges(Optional.empty(), Optional.empty(), Optional.of(email), Optional.empty());
    }

    private static UserChanges active(boolean active) {
        return new UserChanges(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(active));
    }

    @Test
    void changesNameAndEmail() {
        User user = update.execute(admin, workerId, new UserChanges(Optional.of("Wanda"), Optional.of("Díaz"),
                Optional.of(" W2@Demo.test "), Optional.empty()));

        assertThat(user.displayName()).isEqualTo("Wanda Díaz");
        assertThat(user.email()).contains("w2@demo.test");
        assertThat(users.findById(workerId)).contains(user);
    }

    @Test
    void editsFirstAndLastName() {
        User renamed = update.execute(admin, workerId, name("Wandita"));
        User withSurnames = update.execute(admin, workerId, surnames("  Díaz Ruiz "));

        assertThat(renamed.firstName()).isEqualTo("Wandita");
        assertThat(withSurnames.lastName()).contains("Díaz Ruiz");
        assertThat(users.findById(workerId).orElseThrow().displayName()).isEqualTo("Wandita Díaz Ruiz");
    }

    @Test
    void namesCannotBeCleared() {
        assertThatThrownBy(() -> update.execute(admin, workerId, name(" ")))
                .isInstanceOf(ValidationException.class).hasMessage("Falta el nombre");
        assertThatThrownBy(() -> update.execute(admin, workerId, surnames("")))
                .isInstanceOf(ValidationException.class).hasMessage("Faltan los apellidos");
        assertThat(users.findById(workerId).orElseThrow().displayName()).isEqualTo("Wanda Pérez");
    }

    @Test
    void codeNeverChanges() {
        String code = users.findById(workerId).orElseThrow().personCode();

        update.execute(admin, workerId, name("Otro"));
        update.execute(Actors.groupAdmin(), workerId, active(false));
        update.execute(Actors.groupAdmin(), workerId, email("nuevo@demo.test"));

        assertThat(users.findById(workerId).orElseThrow().personCode()).isEqualTo(code);
    }

    @Test
    void duplicateEmailIsConflict() {
        assertThatThrownBy(() -> update.execute(admin, workerId, email("C@demo.test")))
                .isInstanceOf(ConflictException.class).hasMessage("Ya existe un usuario con ese correo");
    }

    @Test
    void sameEmailOnSameUserIsNotConflict() {
        User user = update.execute(admin, workerId, email("W@demo.test"));

        assertThat(user.email()).contains("w@demo.test");
    }

    @Test
    void blankEmailClearsWorkersEmail() {
        assertThat(update.execute(admin, workerId, email("")).email()).isEmpty();
    }

    @Test
    void blankEmailOnAdminIsRejected() {
        assertThatThrownBy(() -> update.execute(Actors.groupAdmin(), adminId, email(" ")))
                .isInstanceOf(ValidationException.class).hasMessage("El administrador necesita correo");
    }

    @Test
    void groupAdminDeactivatesAndReactivatesAPerson() {
        assertThat(update.execute(Actors.groupAdmin(), workerId, active(false)).active()).isFalse();
        assertThat(users.findById(workerId).orElseThrow().active()).isFalse();
        assertThat(memberships.find(workerId, company).orElseThrow().active()).isTrue();

        assertThat(update.execute(Actors.groupAdmin(), workerId, active(true)).active()).isTrue();
    }

    @Test
    void cannotDeactivateTheLastActiveGroupAdmin() {
        UserId gemaId = new UserId(UUID.randomUUID());
        People.groupAdmin(users, gemaId, "Gema", Optional.of("g@demo.test"), true, Optional.of("pw:x"));
        Actor other = new Actor(new UserId(UUID.randomUUID()), "Otra", Role.GROUP_ADMIN, Set.of());

        assertThatThrownBy(() -> update.execute(other, gemaId, active(false))).isInstanceOf(ConflictException.class)
                .hasMessage("Tiene que quedar al menos un administrador del grupo activo");
        assertThat(users.findById(gemaId).orElseThrow().active()).isTrue();
    }

    @Test
    void deactivatesAGroupAdminWhenAnotherIsActive() {
        UserId gemaId = new UserId(UUID.randomUUID());
        People.groupAdmin(users, gemaId, "Gema", Optional.of("g@demo.test"), true, Optional.of("pw:x"));
        People.groupAdmin(users, new UserId(UUID.randomUUID()), "Gael", Optional.of("gael@demo.test"), true,
                Optional.of("pw:x"));

        assertThat(update.execute(Actors.groupAdmin(), gemaId, active(false)).active()).isFalse();
    }

    @Test
    void onlyGroupAdminDeactivatesAPerson() {
        assertThatThrownBy(() -> update.execute(admin, workerId, active(false)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Solo el administrador del grupo activa o desactiva a una persona");
        assertThat(users.findById(workerId).orElseThrow().active()).isTrue();
    }

    @Test
    void companyAdminCannotEditPersonWhoBelongsToAnotherCompany() {
        memberships.save(new Membership(workerId, new CompanyId(UUID.randomUUID()), Set.of(Role.WORKER), true));

        assertThatThrownBy(() -> update.execute(admin, workerId, name("X")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Solo el administrador del grupo puede editar a esta persona");
        assertThat(users.findById(workerId).orElseThrow().firstName()).isEqualTo("Wanda");
        assertThat(update.execute(Actors.groupAdmin(), workerId, name("Wandita")).firstName())
                .isEqualTo("Wandita");
    }

    @Test
    void companyAdminOfTwoCompaniesEditsAPersonWhoIsOnlyInThose() {
        CompanyId second = new CompanyId(UUID.randomUUID());
        memberships.save(new Membership(workerId, second, Set.of(Role.WORKER), true));
        Actor both = new Actor(adminId, "Carlos", Role.COMPANY_ADMIN, Set.of(company, second));

        assertThat(update.execute(both, workerId, name("Wandita")).firstName()).isEqualTo("Wandita");
    }

    @Test
    void onlyTheGroupAdminOrThePersonThemselvesChangesTheEmailOfACompanyAdmin() {
        assertThatThrownBy(() -> update.execute(admin, adminId, email("nuevo@demo.test")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Solo el administrador del grupo puede editar a esta persona");
        assertThat(users.findById(adminId).orElseThrow().email()).contains("c@demo.test");
        assertThat(update.execute(admin, adminId, name("Carlitos")).firstName()).isEqualTo("Carlitos");

        assertThat(update.execute(Actors.groupAdmin(), adminId, email("g@demo.test")).email())
                .contains("g@demo.test");
        Actor herself = new Actor(adminId, "Carlos", Role.COMPANY_ADMIN, Set.of(company));
        assertThat(update.execute(herself, adminId, email("yo@demo.test")).email()).contains("yo@demo.test");
    }

    @Test
    void aPersonWhoAdministersAnotherCompanyKeepsTheirEmailAgainstAnyCompanyAdmin() {
        CompanyId second = new CompanyId(UUID.randomUUID());
        memberships.save(new Membership(workerId, second, Set.of(Role.COMPANY_ADMIN), true));
        memberships.save(new Membership(workerId, company, Set.of(Role.WORKER), true));
        Actor both = new Actor(adminId, "Carlos", Role.COMPANY_ADMIN, Set.of(company, second));

        assertThatThrownBy(() -> update.execute(Actors.companyAdmin(company), workerId, email("x@demo.test")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> update.execute(both, workerId, email("x@demo.test")))
                .isInstanceOf(ForbiddenException.class);
        assertThat(update.execute(Actors.groupAdmin(), workerId, email("x@demo.test")).email())
                .contains("x@demo.test");
    }

    @Test
    void companyAdminEditsTheirOwnNamesEvenIfTheyBelongToAnotherCompany() {
        memberships.save(new Membership(adminId, new CompanyId(UUID.randomUUID()), Set.of(Role.WORKER), true));
        Actor herself = new Actor(adminId, "Carlos", Role.COMPANY_ADMIN, Set.of(company));

        User renamed = update.execute(herself, adminId,
                new UserChanges(Optional.of("Carla"), Optional.of("Gil"), Optional.empty(), Optional.empty()));

        assertThat(renamed.displayName()).isEqualTo("Carla Gil");
    }

    @Test
    void groupAdminEditsTheirOwnNames() {
        UserId gemaId = new UserId(UUID.randomUUID());
        People.groupAdmin(users, gemaId, "Gema", Optional.of("g@demo.test"), true, Optional.of("pw:x"));
        Actor gema = new Actor(gemaId, "Gema", Role.GROUP_ADMIN, Set.of());

        assertThat(update.execute(gema, gemaId, name("Gemma")).firstName()).isEqualTo("Gemma");
    }

    @Test
    void deactivatingClosesTheUsersSessions() {
        String token = sessions.open(adminId);

        update.execute(Actors.groupAdmin(), adminId, active(false));
        update.execute(Actors.groupAdmin(), adminId, active(true));

        assertThat(sessions.resolve(token)).isEmpty();
    }

    @Test
    void companyAdminCannotUpdateTheGroupAdmin() {
        UserId groupAdminId = new UserId(UUID.randomUUID());
        People.groupAdmin(users, groupAdminId, "Gema", Optional.of("g@demo.test"), true, Optional.of("pw:x"));

        assertThatThrownBy(() -> update.execute(admin, groupAdminId, active(false)))
                .isInstanceOf(ForbiddenException.class);
        assertThat(users.findById(groupAdminId).orElseThrow().active()).isTrue();
    }

    @Test
    void companyAdminCannotRenameOrChangeTheEmailOfAGroupAdminWhoWorksInTheirCompany() {
        UserId gemaId = new UserId(UUID.randomUUID());
        People.groupAdmin(users, gemaId, "Gema", Optional.of("g@demo.test"), true, Optional.of("pw:x"));
        memberships.save(new Membership(gemaId, company, Set.of(Role.WORKER), true));

        assertThatThrownBy(() -> update.execute(admin, gemaId, name("Otra")))
                .isInstanceOf(ForbiddenException.class).hasMessage("Solo el administrador del grupo puede editar a esta persona");
        assertThatThrownBy(() -> update.execute(admin, gemaId, email("x@demo.test")))
                .isInstanceOf(ForbiddenException.class).hasMessage("Solo el administrador del grupo puede editar a esta persona");
        assertThat(users.findById(gemaId).orElseThrow().firstName()).isEqualTo("Gema");
        assertThat(users.findById(gemaId).orElseThrow().email()).contains("g@demo.test");
        assertThat(update.execute(Actors.groupAdmin(), gemaId, name("Gemma")).firstName()).isEqualTo("Gemma");
    }

    @Test
    void cannotDeactivateOneself() {
        Actor self = new Actor(adminId, "Carlos", Role.GROUP_ADMIN, Set.of());

        assertThatThrownBy(() -> update.execute(self, adminId, active(false)))
                .isInstanceOf(ValidationException.class).hasMessage("No puedes desactivarte a ti mismo");
        assertThat(users.findById(adminId).orElseThrow().active()).isTrue();
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        Actor stranger = Actors.companyAdmin(new CompanyId(UUID.randomUUID()));

        assertThatThrownBy(() -> update.execute(stranger, workerId, name("X"))).isInstanceOf(ForbiddenException.class);
        assertThat(users.findById(workerId).orElseThrow().firstName()).isEqualTo("Wanda");
    }

    @Test
    void adminOfAnotherCompanyCannotChangeActiveOrEmail() {
        Actor stranger = Actors.companyAdmin(new CompanyId(UUID.randomUUID()));

        assertThatThrownBy(() -> update.execute(stranger, workerId, active(false)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> update.execute(stranger, workerId, email("otro@demo.test")))
                .isInstanceOf(ForbiddenException.class);
        User unchanged = users.findById(workerId).orElseThrow();
        assertThat(unchanged.active()).isTrue();
        assertThat(unchanged.email()).contains("w@demo.test");
    }

    @Test
    void workerCannotUpdate() {
        assertThatThrownBy(() -> update.execute(Actors.worker(company), workerId, name("X")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deletedUserIsNotFound() {
        users.delete(workerId, Instant.now());

        assertThatThrownBy(() -> update.execute(admin, workerId, name("X"))).isInstanceOf(NotFoundException.class);
    }
}
