package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import org.junit.jupiter.api.Test;

class RenameCompanyTest {

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final RenameCompany rename = new RenameCompany(organizations);
    private final CompanyId company = Fixtures.company(organizations);

    @Test
    void companyAdminCannotRename() {
        String before = organizations.findCompany(company).orElseThrow().name();

        assertThatThrownBy(() -> rename.execute(Actors.companyAdmin(company), company, "Nuevo"))
                .isInstanceOf(ForbiddenException.class);
        assertThat(organizations.findCompany(company).orElseThrow().name()).isEqualTo(before);
    }

    @Test
    void groupAdminCanRename() {
        rename.execute(Actors.groupAdmin(), company, "Nuevo");

        assertThat(organizations.findCompany(company).orElseThrow().name()).isEqualTo("Nuevo");
    }
}
