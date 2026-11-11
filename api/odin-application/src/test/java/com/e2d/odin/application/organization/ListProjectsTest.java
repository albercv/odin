package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
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
}
