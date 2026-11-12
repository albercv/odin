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
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import org.junit.jupiter.api.Test;

class ListProjectMembersTest {

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
    private final ListProjectMembers list = new ListProjectMembers(organizations, users, members);
    private final UserId wanda = person("Wanda", Role.WORKER, company, true);

    @Test
    void listsTheMembersWithTheirNames() {
        members.add(obra, wanda);

        assertThat(list.execute(Actors.companyAdmin(company), company, obra))
                .containsExactly(new ProjectMemberView(wanda, "Wanda Pérez"));
    }

    @Test
    void projectWithoutMembersIsEmpty() {
        assertThat(list.execute(Actors.companyAdmin(company), company, obra)).isEmpty();
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        members.add(obra, wanda);

        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(other), company, obra))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void projectOfAnotherCompanyIsNotFound() {
        members.add(obra, wanda);

        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(other), other, obra))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsMembersInTheOrderTheyWereAdded() {
        UserId ana = person("Ana", Role.WORKER, company, true);
        UserId zoe = person("Zoe", Role.WORKER, company, true);
        UserId mia = person("Mia", Role.WORKER, company, true);
        members.add(obra, zoe);
        members.add(obra, mia);
        members.add(obra, ana);

        assertThat(list.execute(Actors.companyAdmin(company), company, obra)).extracting(ProjectMemberView::userId)
                .containsExactly(zoe, mia, ana);
    }
}
