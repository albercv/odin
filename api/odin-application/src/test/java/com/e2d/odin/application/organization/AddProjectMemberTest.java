package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AddProjectMemberTest {

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(organizations);
    private final CompanyId company = Fixtures.company(organizations);
    private final CompanyId other = Fixtures.company(organizations);
    private final ProjectId obra = Fixtures.project(organizations, company);

    private UserId person(String name, Role role, CompanyId in, boolean active) {
        return Fixtures.person(users, memberships, name, role, in, active);
    }
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final AddProjectMember add = new AddProjectMember(organizations, memberships, members, audit);

    @Test
    void addsWorkerOfTheCompany() {
        UserId wanda = person("Wanda", Role.WORKER, company, true);

        add.execute(Actors.companyAdmin(company), company, obra, wanda);

        assertThat(members.listMembers(obra)).containsExactly(wanda);
        assertThat(members.projectsOf(company, wanda)).containsExactly(obra);
    }

    @Test
    void nonWorkerOrForeignPersonIsRejected() {
        UserId adminOnly = person("Carlos", Role.COMPANY_ADMIN, company, true);
        UserId inactive = person("Ines", Role.WORKER, company, false);
        UserId foreign = person("Fabio", Role.WORKER, other, true);
        UserId stranger = new UserId(UUID.randomUUID());

        for (UserId candidate : List.of(adminOnly, inactive, foreign, stranger)) {
            assertThatThrownBy(() -> add.execute(Actors.companyAdmin(company), company, obra, candidate))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Solo un trabajador de la empresa puede ser miembro");
        }
        assertThat(members.listMembers(obra)).isEmpty();
    }

    @Test
    void addingTwiceIsIdempotent() {
        UserId wanda = person("Wanda", Role.WORKER, company, true);

        add.execute(Actors.companyAdmin(company), company, obra, wanda);
        add.execute(Actors.companyAdmin(company), company, obra, wanda);

        assertThat(members.listMembers(obra)).containsExactly(wanda);
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        UserId wanda = person("Wanda", Role.WORKER, company, true);

        assertThatThrownBy(() -> add.execute(Actors.companyAdmin(other), company, obra, wanda))
                .isInstanceOf(ForbiddenException.class);
        assertThat(members.listMembers(obra)).isEmpty();
    }

    @Test
    void projectOfAnotherCompanyIsNotFound() {
        UserId fabio = person("Fabio", Role.WORKER, other, true);

        assertThatThrownBy(() -> add.execute(Actors.companyAdmin(other), other, obra, fabio))
                .isInstanceOf(NotFoundException.class).hasMessage("Proyecto no encontrado");
        assertThat(members.listMembers(obra)).isEmpty();
    }

    @Test
    void workerCannotAddMembers() {
        UserId wanda = person("Wanda", Role.WORKER, company, true);

        assertThatThrownBy(() -> add.execute(Actors.worker(company), company, obra, wanda))
                .isInstanceOf(ForbiddenException.class);
    }
}
