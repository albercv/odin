package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateCompanyTest {

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final CreateCompany create = new CreateCompany(organizations, new SequenceIdGenerator());

    @Test
    void onlyGroupAdmin() {
        assertThatThrownBy(() -> create.execute(Actors.companyAdmin(new CompanyId(UUID.randomUUID())), "Nueva"))
                .isInstanceOf(ForbiddenException.class);
        assertThat(organizations.listCompanies(Actors.groupAdmin().scope())).isEmpty();
    }

    @Test
    void enablesAllCapabilities() {
        Company company = create.execute(Actors.groupAdmin(), "Talleres Demo");

        assertThat(company.capabilities()).isEqualTo(EnumSet.allOf(Capability.class));
        assertThat(organizations.findCompany(company.id())).contains(company);
    }
}
