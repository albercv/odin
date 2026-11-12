package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateMemberTest {

    private static final String ONLY_GROUP_ADMIN =
            "Solo el administrador del grupo da o quita la administración de una empresa";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final FakePasswordHasher passwords = new FakePasswordHasher();
    private final StoredAdminSessions sessions = new StoredAdminSessions(new InMemoryAdminSessionRepository(),
            new FakeSecretHasher(), new SequenceTokenGenerator(), new SessionSettings(), new MutableClock());
    private final UpdateMember update = new UpdateMember(users, memberships, organizations, passwords, sessions);
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private final CompanyId other = new CompanyId(UUID.randomUUID());
    private final UserId workerId = new UserId(UUID.randomUUID());
    private final UserId adminId = new UserId(UUID.randomUUID());
    private final Actor companyAdmin = Actors.companyAdmin(company);
    private final Actor groupAdmin = Actors.groupAdmin();

    UpdateMemberTest() {
        organizations.saveCompany(new Company(company, "A", "AAA", EnumSet.allOf(Capability.class)));
        People.member(users, memberships, workerId, "Wanda", Optional.of("w@demo.test"), Role.WORKER, company, true,
                Optional.empty());
        People.member(users, memberships, adminId, "Carlos", Optional.of("c@demo.test"), Role.COMPANY_ADMIN, company,
                true, Optional.of("pw:x"));
    }

    private static MemberChanges roles(Role... roles) {
        return new MemberChanges(Optional.of(Set.of(roles)), Optional.empty(), Optional.empty());
    }

    private static MemberChanges active(boolean active) {
        return new MemberChanges(Optional.empty(), Optional.of(active), Optional.empty());
    }

    private Membership stored(UserId userId, CompanyId inCompany) {
        return memberships.find(userId, inCompany).orElseThrow();
    }

    private UserId groupAdminWorkingHere() {
        UserId gemaId = new UserId(UUID.randomUUID());
        People.groupAdmin(users, gemaId, "Gema", Optional.of("g@demo.test"), true, Optional.of("pw:x"));
        memberships.save(new Membership(gemaId, company, Set.of(Role.WORKER), true));
        return gemaId;
    }

    @Test
    void onlyTheGroupAdminActsOnTheGroupAdminsMembership() {
        UserId gema = groupAdminWorkingHere();

        assertThatThrownBy(() -> update.execute(companyAdmin, company, gema, active(false)))
                .isInstanceOf(ForbiddenException.class).hasMessage("Solo el administrador del grupo puede editar a esta persona");
        assertThat(stored(gema, company).active()).isTrue();
        assertThat(update.execute(groupAdmin, company, gema, active(false)).active()).isFalse();
    }

    @Test
    void groupAdminCannotGetCompanyAdminThroughAnUpdateEither() {
        UserId gema = groupAdminWorkingHere();

        assertThatThrownBy(() -> update.execute(groupAdmin, company, gema, roles(Role.COMPANY_ADMIN, Role.WORKER)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("El administrador del grupo ya administra todas las empresas");
        assertThat(stored(gema, company).roles()).containsExactly(Role.WORKER);
    }

    @Test
    void unknownCompanyChangesNothing() {
        CompanyId ghost = new CompanyId(UUID.randomUUID());
        memberships.save(new Membership(workerId, ghost, Set.of(Role.WORKER), true));

        assertThatThrownBy(() -> update.execute(groupAdmin, ghost, workerId, active(false)))
                .isInstanceOf(NotFoundException.class).hasMessage("Empresa no encontrada");
        assertThat(stored(workerId, ghost).active()).isTrue();
    }

    @Test
    void addsWorkerRoleToAnAdmin() {
        MemberView view = update.execute(companyAdmin, company, adminId, roles(Role.COMPANY_ADMIN, Role.WORKER));

        assertThat(view.roles()).containsExactlyInAnyOrder(Role.COMPANY_ADMIN, Role.WORKER);
        assertThat(view.memberCode()).isEqualTo(users.findById(adminId).orElseThrow().personCode() + "-AAA");
        assertThat(stored(adminId, company).roles()).containsExactlyInAnyOrder(Role.COMPANY_ADMIN, Role.WORKER);
    }

    @Test
    void companyAdminCannotGrantOrRevokeCompanyAdmin() {
        assertThatThrownBy(() -> update.execute(companyAdmin, company, workerId, roles(Role.WORKER, Role.COMPANY_ADMIN)))
                .isInstanceOf(ForbiddenException.class).hasMessage(ONLY_GROUP_ADMIN);
        assertThatThrownBy(() -> update.execute(companyAdmin, company, adminId, roles(Role.WORKER)))
                .isInstanceOf(ForbiddenException.class).hasMessage(ONLY_GROUP_ADMIN);
        assertThat(stored(workerId, company).roles()).containsExactly(Role.WORKER);
        assertThat(stored(adminId, company).roles()).containsExactly(Role.COMPANY_ADMIN);
    }

    @Test
    void grantingAdminToPersonWithoutPasswordNeedsOne() {
        MemberChanges withoutPassword = roles(Role.WORKER, Role.COMPANY_ADMIN);

        assertThatThrownBy(() -> update.execute(groupAdmin, company, workerId, withoutPassword))
                .isInstanceOf(ValidationException.class).hasMessage("Hace falta una contraseña para administrar");
        assertThat(stored(workerId, company).isCompanyAdmin()).isFalse();

        update.execute(groupAdmin, company, workerId, new MemberChanges(Optional.of(Set.of(Role.COMPANY_ADMIN)),
                Optional.empty(), Optional.of("clave-larga-123")));

        assertThat(stored(workerId, company).isCompanyAdmin()).isTrue();
        assertThat(users.findById(workerId).orElseThrow().passwordHash()).contains(passwords.hash("clave-larga-123"));
    }

    @Test
    void passwordOfAPersonWhoAlreadyHasOneIsNotOverwritten() {
        users.save(new com.e2d.odin.domain.identity.User(workerId, "Wanda", Optional.of("Pérez"), People.nextCode(),
                Optional.of("w@demo.test"), false, true,
                Optional.of("pw:propia")));

        update.execute(groupAdmin, company, workerId, new MemberChanges(Optional.of(Set.of(Role.COMPANY_ADMIN)),
                Optional.empty(), Optional.of("otra-clave-1234")));

        assertThat(users.findById(workerId).orElseThrow().passwordHash()).contains("pw:propia");
    }

    @Test
    void passwordWithoutGrantingAdministrationIsRejected() {
        MemberChanges changes = new MemberChanges(Optional.empty(), Optional.empty(), Optional.of("clave-larga-123"));

        assertThatThrownBy(() -> update.execute(groupAdmin, company, workerId, changes))
                .isInstanceOf(ValidationException.class).hasMessage(UserAdministration.WORKER_HAS_NO_PASSWORD);
        assertThatThrownBy(() -> update.execute(groupAdmin, company, adminId, changes))
                .isInstanceOf(ValidationException.class).hasMessage(UserAdministration.WORKER_HAS_NO_PASSWORD);
        assertThat(users.findById(workerId).orElseThrow().passwordHash()).isEmpty();
    }

    @Test
    void companyAdminCannotActivateOrDeactivateAnAdminMembership() {
        assertThatThrownBy(() -> update.execute(companyAdmin, company, adminId, active(false)))
                .isInstanceOf(ForbiddenException.class).hasMessage(ONLY_GROUP_ADMIN);
        update.execute(groupAdmin, company, adminId, active(false));

        assertThatThrownBy(() -> update.execute(companyAdmin, company, adminId, active(true)))
                .isInstanceOf(ForbiddenException.class).hasMessage(ONLY_GROUP_ADMIN);
        assertThat(stored(adminId, company).active()).isFalse();
    }

    @Test
    void companyAdminMayOnlyAddOrRemoveWorkerOnAnAdminMembership() {
        update.execute(companyAdmin, company, adminId, roles(Role.COMPANY_ADMIN, Role.WORKER));
        assertThat(stored(adminId, company).roles()).containsExactlyInAnyOrder(Role.COMPANY_ADMIN, Role.WORKER);

        update.execute(companyAdmin, company, adminId, roles(Role.COMPANY_ADMIN));
        assertThat(stored(adminId, company).roles()).containsExactly(Role.COMPANY_ADMIN);

        assertThatThrownBy(() -> update.execute(companyAdmin, company, adminId, new MemberChanges(
                Optional.of(Set.of(Role.COMPANY_ADMIN, Role.WORKER)), Optional.of(false), Optional.empty())))
                .isInstanceOf(ForbiddenException.class).hasMessage(ONLY_GROUP_ADMIN);
        assertThat(stored(adminId, company).roles()).containsExactly(Role.COMPANY_ADMIN);
        assertThat(stored(adminId, company).active()).isTrue();
    }

    @Test
    void removingLastRoleIsRejected() {
        assertThatThrownBy(() -> update.execute(groupAdmin, company, workerId, roles()))
                .isInstanceOf(ValidationException.class).hasMessage("Una pertenencia necesita al menos un papel");
        assertThat(stored(workerId, company).roles()).containsExactly(Role.WORKER);
    }

    @Test
    void cannotRemoveOwnActingRole() {
        Actor self = new Actor(adminId, "Carlos", Role.COMPANY_ADMIN, Set.of(company));

        assertThatThrownBy(() -> update.execute(self, company, adminId, roles(Role.WORKER)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("No puedes quitarte el papel con el que estás actuando");
        assertThatThrownBy(() -> update.execute(self, company, adminId, active(false)))
                .isInstanceOf(ValidationException.class).hasMessage("No puedes desactivarte a ti mismo");
        assertThat(stored(adminId, company).roles()).containsExactly(Role.COMPANY_ADMIN);
        assertThat(stored(adminId, company).active()).isTrue();
    }

    @Test
    void deactivatesAndReactivatesMembership() {
        assertThat(update.execute(companyAdmin, company, workerId, active(false)).active()).isFalse();
        assertThat(stored(workerId, company).active()).isFalse();
        assertThat(users.findById(workerId).orElseThrow().active()).isTrue();

        assertThat(update.execute(companyAdmin, company, workerId, active(true)).active()).isTrue();
        assertThat(stored(workerId, company).active()).isTrue();
    }

    @Test
    void losingAdminEverywhereClosesSessions() {
        String token = sessions.open(adminId);

        update.execute(groupAdmin, company, adminId, roles(Role.WORKER));

        assertThat(sessions.resolve(token)).isEmpty();
    }

    @Test
    void deactivatingTheOnlyAdminMembershipClosesSessions() {
        String token = sessions.open(adminId);

        update.execute(groupAdmin, company, adminId, active(false));

        assertThat(sessions.resolve(token)).isEmpty();
    }

    @Test
    void losingAdminInOneOfTwoCompaniesKeepsSessions() {
        memberships.save(new Membership(adminId, other, Set.of(Role.COMPANY_ADMIN), true));
        String token = sessions.open(adminId);

        update.execute(groupAdmin, company, adminId, roles(Role.WORKER));

        assertThat(sessions.resolve(token)).contains(adminId);
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        Actor stranger = Actors.companyAdmin(other);

        assertThatThrownBy(() -> update.execute(stranger, company, workerId, active(false)))
                .isInstanceOf(ForbiddenException.class);
        assertThat(stored(workerId, company).active()).isTrue();
    }

    @Test
    void workerCannotUpdate() {
        assertThatThrownBy(() -> update.execute(Actors.worker(company), company, workerId, active(false)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void personWhoDoesNotBelongToTheCompanyIsNotFound() {
        UserId stranger = new UserId(UUID.randomUUID());
        People.member(users, memberships, stranger, "Olga", Optional.empty(), Role.WORKER, other, true,
                Optional.empty());

        assertThatThrownBy(() -> update.execute(companyAdmin, company, stranger, active(false)))
                .isInstanceOf(NotFoundException.class);
        assertThat(stored(stranger, other).active()).isTrue();
    }
}
