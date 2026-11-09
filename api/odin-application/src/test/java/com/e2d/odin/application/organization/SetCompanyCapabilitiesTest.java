package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SetCompanyCapabilitiesTest {

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final SetCompanyCapabilities set = new SetCompanyCapabilities(organizations);
    private final CompanyId company = Fixtures.companyWith(organizations, EnumSet.of(Capability.SEARCH_KNOWLEDGE));

    @Test
    void companyAdminCannotChangeCapabilities() {
        Set<Capability> all = EnumSet.allOf(Capability.class);

        assertThatThrownBy(() -> set.execute(Actors.companyAdmin(company), company, all))
                .isInstanceOf(ForbiddenException.class);
        assertThat(organizations.findCompany(company).orElseThrow().capabilities())
                .containsExactly(Capability.SEARCH_KNOWLEDGE);
    }

    @Test
    void companyAdminOfOtherCompanyCannotChangeCapabilities() {
        CompanyId other = Fixtures.company(organizations);

        assertThatThrownBy(() -> set.execute(Actors.companyAdmin(other), company, EnumSet.allOf(Capability.class)))
                .isInstanceOf(ForbiddenException.class);
        assertThat(organizations.findCompany(company).orElseThrow().capabilities())
                .containsExactly(Capability.SEARCH_KNOWLEDGE);
    }

    @Test
    void groupAdminCanChangeCapabilities() {
        set.execute(Actors.groupAdmin(), company, EnumSet.noneOf(Capability.class));

        assertThat(organizations.findCompany(company).orElseThrow().capabilities()).isEmpty();
    }
}
