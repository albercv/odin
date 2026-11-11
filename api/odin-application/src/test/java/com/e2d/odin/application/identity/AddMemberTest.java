package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.SequencePersonCodeGenerator;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AddMemberTest {

    private static final String PASSWORD = "clave-larga-123";

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final InMemoryProjectMemberRepository projectMembers = new InMemoryProjectMemberRepository(organizations);
    private final FakePasswordHasher passwords = new FakePasswordHasher();
    private final SequencePersonCodeGenerator codes = new SequencePersonCodeGenerator("7F3K", "9ZQ2", "K4MN");
    private final AddMember add = new AddMember(users, memberships, organizations, projectMembers,
            passwords, new SequenceIdGenerator(), codes);
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private final CompanyId other = new CompanyId(UUID.randomUUID());
    private final Actor groupAdmin = Actors.groupAdmin();

    AddMemberTest() {
        organizations.saveCompany(new Company(company, "A", "E2D", EnumSet.allOf(Capability.class)));
        organizations.saveCompany(new Company(other, "B", "BBB", EnumSet.allOf(Capability.class)));
    }

    private static NewMember member(String email, Set<Role> roles, String password) {
        return new NewMember("Pepe", "Gómez", Optional.ofNullable(email), roles, Optional.ofNullable(password), Set.of());
    }

    private User existingAdminOfOtherCompany() {
        return People.member(users, memberships, new UserId(UUID.randomUUID()), "Carlos", Optional.of("c@demo.test"),
                Role.COMPANY_ADMIN, other, true, Optional.of(passwords.hash("clave-de-carlos-1")));
    }

    @Test
    void newPersonGetsUserAndMembership() {
        MemberAdded added = add.execute(Actors.companyAdmin(company), company,
                new NewMember("Wanda", "Pérez", Optional.of(" W@Demo.test "), Set.of(Role.WORKER), Optional.empty(), Set.of()));

        assertThat(added.alreadyExisted()).isFalse();
        assertThat(added.user().displayName()).isEqualTo("Wanda Pérez");
        assertThat(added.user().email()).contains("w@demo.test");
        assertThat(added.user().groupAdmin()).isFalse();
        assertThat(added.membership().roles()).containsExactly(Role.WORKER);
        assertThat(added.membership().active()).isTrue();
        assertThat(users.findById(added.user().id())).contains(added.user());
        assertThat(memberships.find(added.user().id(), company)).contains(added.membership());
    }

    @Test
    void newPersonGetsAGeneratedCode() {
        MemberAdded added = add.execute(Actors.companyAdmin(company), company,
                member("a@demo.test", Set.of(Role.WORKER), null));
        MemberAdded another = add.execute(Actors.companyAdmin(company), company,
                member("b@demo.test", Set.of(Role.WORKER), null));

        assertThat(added.user().personCode()).isEqualTo("7F3K");
        assertThat(another.user().personCode()).isEqualTo("9ZQ2");
        assertThat(users.findById(added.user().id()).orElseThrow().personCode()).isEqualTo("7F3K");
    }

    @Test
    void existingPersonKeepsNameAndCode() {
        User carlos = existingAdminOfOtherCompany();

        MemberAdded added = add.execute(groupAdmin, company, new NewMember("Otro", "Distinto",
                Optional.of("c@demo.test"), Set.of(Role.WORKER), Optional.empty(), Set.of()));

        assertThat(added.user().firstName()).isEqualTo(carlos.firstName());
        assertThat(added.user().lastName()).isEqualTo(carlos.lastName());
        assertThat(added.user().personCode()).isEqualTo(carlos.personCode());
        assertThat(codes.calls()).isZero();
    }

    @Test
    void missingFirstOrLastNameIsRejected() {
        for (String[] names : new String[][] {{null, "Pérez"}, {" ", "Pérez"}, {"Pepe", null}, {"Pepe", " "}}) {
            assertThatThrownBy(() -> add.execute(Actors.companyAdmin(company), company, new NewMember(names[0],
                    names[1], Optional.empty(), Set.of(Role.WORKER), Optional.empty(), Set.of())))
                    .isInstanceOf(ValidationException.class).hasMessage("Faltan el nombre o los apellidos");
        }
        assertThat(memberships.listByCompany(company)).isEmpty();
    }

    @Test
    void memberCodeJoinsPersonAndCompanyCodes() {
        MemberAdded added = add.execute(Actors.companyAdmin(company), company,
                member(null, Set.of(Role.WORKER), null));

        MemberView view = MemberView.of(added.user(), added.membership(), added.company().code(), groupAdmin);

        assertThat(view.memberCode()).isEqualTo("7F3K-E2D");
        assertThat(view.personCode()).isEqualTo("7F3K");
    }

    @Test
    void existingEmailGetsSecondMembershipAndKeepsNameAndPassword() {
        User carlos = existingAdminOfOtherCompany();

        MemberAdded added = add.execute(groupAdmin, company,
                new NewMember("Otro nombre", "Otros apellidos", Optional.of("C@demo.test"), Set.of(Role.COMPANY_ADMIN),
                        Optional.of("otra-clave-1234"), Set.of()));

        assertThat(added.alreadyExisted()).isTrue();
        assertThat(added.user()).isEqualTo(carlos);
        assertThat(users.findById(carlos.id())).contains(carlos);
        assertThat(memberships.find(carlos.id(), company)).isPresent();
        assertThat(memberships.find(carlos.id(), other)).isPresent();
    }

    @Test
    void sameCompanyTwiceIsConflict() {
        User carlos = existingAdminOfOtherCompany();

        assertThatThrownBy(() -> add.execute(Actors.companyAdmin(other), other,
                member("c@demo.test", Set.of(Role.WORKER), null)))
                .isInstanceOf(ConflictException.class).hasMessage("Esa persona ya pertenece a esta empresa");
        assertThat(memberships.find(carlos.id(), other).orElseThrow().roles()).containsExactly(Role.COMPANY_ADMIN);
    }

    @Test
    void adminRoleNeedsPassword() {
        assertThatThrownBy(() -> add.execute(groupAdmin, company,
                member("p@demo.test", Set.of(Role.COMPANY_ADMIN), null)))
                .isInstanceOf(ValidationException.class).hasMessage("Hace falta una contraseña para administrar");
        assertThat(users.findByEmail("p@demo.test")).isEmpty();
        assertThat(memberships.listByCompany(company)).isEmpty();
    }

    @Test
    void adminRoleForExistingPersonWithoutPasswordNeedsOneAndStoresIt() {
        UserId wandaId = new UserId(UUID.randomUUID());
        People.member(users, memberships, wandaId, "Wanda", Optional.of("w@demo.test"), Role.WORKER, other, true,
                Optional.empty());

        assertThatThrownBy(() -> add.execute(groupAdmin, company,
                member("w@demo.test", Set.of(Role.COMPANY_ADMIN), null)))
                .isInstanceOf(ValidationException.class).hasMessage("Hace falta una contraseña para administrar");
        add.execute(groupAdmin, company, member("w@demo.test", Set.of(Role.COMPANY_ADMIN), PASSWORD));

        assertThat(users.findById(wandaId).orElseThrow().passwordHash()).contains(passwords.hash(PASSWORD));
    }

    @Test
    void adminNeedsEmailAndAPasswordOfEnoughLength() {
        assertThatThrownBy(() -> add.execute(groupAdmin, company, member(null, Set.of(Role.COMPANY_ADMIN), PASSWORD)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> add.execute(groupAdmin, company,
                member("p@demo.test", Set.of(Role.COMPANY_ADMIN), "corta")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void adminAndWorkerRolesTogether() {
        MemberAdded added = add.execute(groupAdmin, company,
                member("p@demo.test", Set.of(Role.COMPANY_ADMIN, Role.WORKER), PASSWORD));

        assertThat(added.membership().roles()).containsExactlyInAnyOrder(Role.COMPANY_ADMIN, Role.WORKER);
        assertThat(added.user().passwordHash()).contains(passwords.hash(PASSWORD));
    }

    @Test
    void workerOnlyHasNoPassword() {
        assertThatThrownBy(() -> add.execute(Actors.companyAdmin(company), company,
                member(null, Set.of(Role.WORKER), "ignorada-123456")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Un trabajador no tiene contraseña: entra con su clave personal");

        MemberAdded added = add.execute(Actors.companyAdmin(company), company,
                member(null, Set.of(Role.WORKER), " "));

        assertThat(added.user().passwordHash()).isEmpty();
        assertThat(added.user().email()).isEmpty();
    }

    @Test
    void companyAdminCannotGrantCompanyAdmin() {
        assertThatThrownBy(() -> add.execute(Actors.companyAdmin(company), company,
                member("p@demo.test", Set.of(Role.COMPANY_ADMIN), PASSWORD)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Solo el administrador del grupo da o quita la administración de una empresa");
        assertThat(users.findByEmail("p@demo.test")).isEmpty();
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        assertThatThrownBy(() -> add.execute(Actors.companyAdmin(company), other,
                member(null, Set.of(Role.WORKER), null))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> add.execute(Actors.worker(company), company,
                member(null, Set.of(Role.WORKER), null))).isInstanceOf(ForbiddenException.class);
        assertThat(memberships.listByCompany(other)).isEmpty();
    }

    @Test
    void emptyRolesAreRejected() {
        assertThatThrownBy(() -> add.execute(groupAdmin, company, member(null, Set.of(), null)))
                .isInstanceOf(ValidationException.class).hasMessage("Una pertenencia necesita al menos un papel");
    }

    @Test
    void groupAdminRoleIsRejected() {
        assertThatThrownBy(() -> add.execute(groupAdmin, company, member(null, Set.of(Role.GROUP_ADMIN), null)))
                .isInstanceOf(ValidationException.class);
    }

    private static NewMember worker(String email, Set<ProjectId> projectIds) {
        return new NewMember("Wanda", "Pérez", Optional.of(email), Set.of(Role.WORKER), Optional.empty(), projectIds);
    }

    @Test
    void addsProjectsOnCreation() {
        ProjectId obra = Fixtures.project(organizations, company);
        ProjectId reforma = Fixtures.project(organizations, company);

        MemberAdded added = add.execute(Actors.companyAdmin(company), company,
                worker("w@demo.test", Set.of(obra, reforma)));

        assertThat(projectMembers.projectsOf(company, added.user().id())).containsExactlyInAnyOrder(obra, reforma);
    }

    @Test
    void addsProjectsToAPersonWhoAlreadyExistedInTheGroup() {
        existingAdminOfOtherCompany();
        ProjectId obra = Fixtures.project(organizations, company);

        MemberAdded added = add.execute(Actors.companyAdmin(company), company,
                worker("c@demo.test", Set.of(obra)));

        assertThat(added.alreadyExisted()).isTrue();
        assertThat(projectMembers.projectsOf(company, added.user().id())).containsExactly(obra);
    }

    @Test
    void projectOfAnotherCompanyIsRejectedAndNothingIsSaved() {
        ProjectId ofOther = Fixtures.project(organizations, other);
        ProjectId deleted = Fixtures.project(organizations, company);
        organizations.deleteProject(company, deleted, Fixtures.NOW);

        for (ProjectId project : List.of(ofOther, deleted, new ProjectId(UUID.randomUUID()))) {
            assertThatThrownBy(() -> add.execute(Actors.companyAdmin(company), company,
                    worker("w@demo.test", Set.of(project))))
                    .isInstanceOf(NotFoundException.class).hasMessage("Proyecto no encontrado");
        }
        assertThat(users.findByEmail("w@demo.test")).isEmpty();
        assertThat(memberships.listByCompany(company)).isEmpty();
        assertThat(projectMembers.listMembers(ofOther)).isEmpty();
    }

    @Test
    void projectsOnlyForWorkers() {
        ProjectId obra = Fixtures.project(organizations, company);

        assertThatThrownBy(() -> add.execute(groupAdmin, company, new NewMember("Carlos", "Pérez",
                Optional.of("c2@demo.test"), Set.of(Role.COMPANY_ADMIN), Optional.of(PASSWORD), Set.of(obra))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Solo un trabajador de la empresa puede ser miembro");
        assertThat(users.findByEmail("c2@demo.test")).isEmpty();
        assertThat(projectMembers.listMembers(obra)).isEmpty();
    }
}
