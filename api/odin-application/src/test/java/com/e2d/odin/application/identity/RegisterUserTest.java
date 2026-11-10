package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegisterUserTest {

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final RegisterUser register = new RegisterUser(users, memberships, organizations, new FakePasswordHasher(),
            new SequenceIdGenerator());
    private final CompanyId company = new CompanyId(UUID.randomUUID());
    private final CompanyId other = new CompanyId(UUID.randomUUID());

    RegisterUserTest() {
        organizations.saveCompany(new Company(company, "A", EnumSet.allOf(Capability.class)));
        organizations.saveCompany(new Company(other, "B", EnumSet.allOf(Capability.class)));
    }

    private static NewUser admin(String email, String password) {
        return new NewUser("Pepe", Optional.ofNullable(email), Role.COMPANY_ADMIN, Optional.ofNullable(password));
    }

    @Test
    void companyAdminCannotCreateCompanyAdmin() {
        Actor companyAdmin = Actors.companyAdmin(company);

        assertThatThrownBy(() -> register.execute(companyAdmin, company, admin("p@demo.test", "clave-larga-123")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void companyAdminCannotRegisterInOtherCompany() {
        NewUser worker = new NewUser("Wanda", Optional.empty(), Role.WORKER, Optional.empty());

        assertThatThrownBy(() -> register.execute(Actors.companyAdmin(company), other, worker))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminNeedsEmailAndPassword() {
        Actor groupAdmin = Actors.groupAdmin();

        assertThatThrownBy(() -> register.execute(groupAdmin, company, admin(null, "clave-larga-123")))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> register.execute(groupAdmin, company, admin("p@demo.test", null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> register.execute(groupAdmin, company, admin("p@demo.test", "corta")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void duplicateEmailConflicts() {
        Actor groupAdmin = Actors.groupAdmin();
        register.execute(groupAdmin, company, admin("p@demo.test", "clave-larga-123"));

        assertThatThrownBy(() -> register.execute(groupAdmin, other, admin("P@demo.test", "clave-larga-123")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void workerWithPasswordIsRejected() {
        NewUser worker = new NewUser("Wanda", Optional.empty(), Role.WORKER, Optional.of("ignorada-123456"));

        assertThatThrownBy(() -> register.execute(Actors.companyAdmin(company), company, worker))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Un trabajador no tiene contraseña: entra con su clave personal");
    }

    @Test
    void workerWithoutEmailIsCreated() {
        NewUser worker = new NewUser("Wanda", Optional.empty(), Role.WORKER, Optional.of(" "));

        UserSummary summary = register.execute(Actors.companyAdmin(company), company, worker);

        assertThat(summary.user().passwordHash()).isEmpty();
        assertThat(summary.user().email()).isEmpty();
        assertThat(summary.active()).isTrue();
        assertThat(summary.role()).isEqualTo(Role.WORKER);
        assertThat(summary.companyId()).contains(company);
        assertThat(memberships.find(summary.user().id(), company)).isPresent();
    }

    @Test
    void deletedUsersEmailCanBeReused() {
        Actor groupAdmin = Actors.groupAdmin();
        User first = register.execute(groupAdmin, company, admin("p@demo.test", "clave-larga-123")).user();
        users.delete(first.id(), java.time.Instant.now());

        User second = register.execute(groupAdmin, company, admin("p@demo.test", "clave-larga-123")).user();

        assertThat(second.id()).isNotEqualTo(first.id());
    }

    @Test
    void registersCompanyAdminWithHashedPassword() {
        User user = register.execute(Actors.groupAdmin(), company, admin("p@demo.test", "clave-larga-123")).user();

        assertThat(user.passwordHash()).contains("pw:clave-larga-123");
        assertThat(users.findById(user.id())).contains(user);
    }
}
