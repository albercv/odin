package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.domain.organization.ProjectSummary;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListProjectsTest {

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final ListProjects list = new ListProjects(organizations);
    private final CompanyId company = Fixtures.company(organizations);

    @Test
    void otherCompanyAdminCannotListProjects() {
        Fixtures.project(organizations, company);
        CompanyId other = new CompanyId(UUID.randomUUID());

        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(other), company))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ownCompanyAdminListsProjects() {
        Fixtures.project(organizations, company);

        assertThat(list.execute(Actors.companyAdmin(company), company)).hasSize(1);
    }

    @Test
    void eachProjectCarriesItsMemberCount() {
        InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(organizations);
        var obra = Fixtures.project(organizations, company);
        Fixtures.project(organizations, company);
        CompanyId other = Fixtures.company(organizations);
        var ofOther = Fixtures.project(organizations, other);
        members.add(obra, new UserId(UUID.randomUUID()));
        members.add(obra, new UserId(UUID.randomUUID()));
        members.add(ofOther, new UserId(UUID.randomUUID()));

        assertThat(list.execute(Actors.companyAdmin(company), company)).extracting(ProjectSummary::memberCount)
                .containsExactly(2, 0);
    }
}
