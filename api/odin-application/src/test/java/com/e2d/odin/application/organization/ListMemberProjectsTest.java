package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import org.junit.jupiter.api.Test;

class ListMemberProjectsTest {

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
    private final ListMemberProjects list = new ListMemberProjects(organizations, memberships, members);
    private final UserId wanda = person("Wanda", Role.WORKER, company, true);

    @Test
    void listsOnlyTheProjectsOfThePersonInThatCompany() {
        ProjectId reforma = Fixtures.companyWideProject(organizations, company);
        Fixtures.project(organizations, company);
        ProjectId deleted = Fixtures.project(organizations, company);
        members.add(obra, wanda);
        members.add(reforma, wanda);
        members.add(deleted, wanda);
        organizations.deleteProject(company, deleted, Fixtures.NOW);

        assertThat(list.execute(Actors.companyAdmin(company), company, wanda)).extracting(Project::id)
                .containsExactlyInAnyOrder(obra, reforma);
    }

    @Test
    void aProjectOfAnotherCompanyDoesNotCount() {
        ProjectId ofOther = Fixtures.project(organizations, other);
        members.add(ofOther, wanda);

        assertThat(list.execute(Actors.companyAdmin(company), company, wanda)).isEmpty();
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(other), company, wanda))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void personOutsideTheCompanyIsNotFound() {
        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(other), other, wanda))
                .isInstanceOf(NotFoundException.class);
    }
}
