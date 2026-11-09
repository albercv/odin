package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import org.junit.jupiter.api.Test;

class ListCompaniesTest {

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final ListCompanies list = new ListCompanies(organizations);
    private final CompanyId own = Fixtures.company(organizations);
    private final CompanyId other = Fixtures.company(organizations);

    @Test
    void companyAdminSeesOnlyOwnCompany() {
        assertThat(list.execute(Actors.companyAdmin(own))).extracting(Company::id).containsExactly(own);
    }

    @Test
    void groupAdminSeesAllCompanies() {
        assertThat(list.execute(Actors.groupAdmin())).extracting(Company::id).containsExactlyInAnyOrder(own, other);
    }

    @Test
    void workerCannotListCompanies() {
        assertThatThrownBy(() -> list.execute(Actors.worker(own))).isInstanceOf(ForbiddenException.class);
    }
}
