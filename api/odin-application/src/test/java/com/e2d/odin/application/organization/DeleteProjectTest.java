package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeleteProjectTest {

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final DeleteProject delete = new DeleteProject(organizations, new MutableClock(), audit);
    private final CompanyId company = Fixtures.company(organizations);
    private final ProjectId project = Fixtures.companyWideProject(organizations, company);

    @Test
    void deletedProjectDisappearsFromListAndMe() {
        delete.execute(Actors.companyAdmin(company), company, project);

        assertThat(organizations.listProjects(company)).isEmpty();
        assertThat(organizations.findProject(company, project)).isEmpty();
        assertThat(Fixtures.describeMe(organizations, new InMemoryProjectMemberRepository(organizations))
                .execute(Actors.worker(company)).projects()).isEmpty();
    }

    @Test
    void deletingTwiceIsNotFound() {
        delete.execute(Actors.companyAdmin(company), company, project);

        assertThatThrownBy(() -> delete.execute(Actors.companyAdmin(company), company, project))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        CompanyId stranger = new CompanyId(UUID.randomUUID());

        assertThatThrownBy(() -> delete.execute(Actors.companyAdmin(stranger), company, project))
                .isInstanceOf(ForbiddenException.class);
        assertThat(organizations.findProject(company, project)).isPresent();
    }
}
