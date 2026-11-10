package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RenameProjectTest {

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final RenameProject rename = new RenameProject(organizations);
    private final CompanyId company = Fixtures.company(organizations);
    private final CreateProject create = new CreateProject(organizations, new SequenceIdGenerator());
    private final Project obra = create.execute(Actors.companyAdmin(company), company, "Obra");

    @Test
    void renames() {
        Project renamed = rename.execute(Actors.companyAdmin(company), company, obra.id(), "  Reforma ");

        assertThat(renamed.name()).isEqualTo("Reforma");
        assertThat(organizations.findProject(company, obra.id())).contains(renamed);
    }

    @Test
    void duplicateNameIsConflict() {
        create.execute(Actors.companyAdmin(company), company, "Reforma");

        assertThatThrownBy(() -> rename.execute(Actors.companyAdmin(company), company, obra.id(), "reforma"))
                .isInstanceOf(ConflictException.class).hasMessage("Ya existe un proyecto con ese nombre");
        assertThat(organizations.findProject(company, obra.id()).orElseThrow().name()).isEqualTo("Obra");
    }

    @Test
    void sameNameIsNotConflict() {
        Project renamed = rename.execute(Actors.companyAdmin(company), company, obra.id(), "OBRA");

        assertThat(renamed.name()).isEqualTo("OBRA");
    }

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> rename.execute(Actors.companyAdmin(company), company, obra.id(), "  "))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void projectOfAnotherCompanyIsNotFound() {
        CompanyId other = Fixtures.company(organizations);

        assertThatThrownBy(() -> rename.execute(Actors.companyAdmin(other), other, obra.id(), "Reforma"))
                .isInstanceOf(NotFoundException.class);
        assertThat(organizations.findProject(company, obra.id()).orElseThrow().name()).isEqualTo("Obra");
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        CompanyId stranger = new CompanyId(UUID.randomUUID());

        assertThatThrownBy(() -> rename.execute(Actors.companyAdmin(stranger), company, obra.id(), "Reforma"))
                .isInstanceOf(ForbiddenException.class);
        assertThat(organizations.findProject(company, obra.id()).orElseThrow().name()).isEqualTo("Obra");
    }
}
