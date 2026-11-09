package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateProjectTest {

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final CreateProject create = new CreateProject(organizations, new SequenceIdGenerator());
    private final CompanyId company = Fixtures.company(organizations);

    @Test
    void otherCompanyAdminCannotCreateProject() {
        CompanyId other = new CompanyId(UUID.randomUUID());

        assertThatThrownBy(() -> create.execute(Actors.companyAdmin(other), company, "Obra"))
                .isInstanceOf(ForbiddenException.class);
        assertThat(organizations.listProjects(company)).isEmpty();
    }

    @Test
    void workerCannotCreateProject() {
        assertThatThrownBy(() -> create.execute(Actors.worker(company), company, "Obra"))
                .isInstanceOf(ForbiddenException.class);
        assertThat(organizations.listProjects(company)).isEmpty();
    }

    @Test
    void ownCompanyAdminCreatesProject() {
        create.execute(Actors.companyAdmin(company), company, "Obra");

        assertThat(organizations.listProjects(company)).hasSize(1);
    }
}
