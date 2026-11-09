package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DescribeMeTest {

    @Test
    void returnsCompanyCapabilitiesAndProjects() {
        InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
        CompanyId id = new CompanyId(UUID.randomUUID());
        Company company = new Company(id, "Talleres Demo", EnumSet.of(Capability.SEARCH_KNOWLEDGE));
        Project project = new Project(new ProjectId(UUID.randomUUID()), id, "Obra 1");
        organizations.saveCompany(company);
        organizations.saveProject(project);
        Actor worker = Actors.worker(id);

        MeView me = new DescribeMe(organizations).execute(worker);

        assertThat(me.userId()).isEqualTo(worker.userId());
        assertThat(me.company().capabilities()).containsExactly(Capability.SEARCH_KNOWLEDGE);
        assertThat(me.projects()).containsExactly(project);
    }
}
