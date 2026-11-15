package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.ProjectLabel;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateCompanyTest {

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final UpdateCompany update = new UpdateCompany(organizations);
    private final CompanyId company = Fixtures.company(organizations);

    private static CompanyChanges name(String name) {
        return new CompanyChanges(Optional.of(name), Optional.empty());
    }

    private static CompanyChanges code(String code) {
        return new CompanyChanges(Optional.empty(), Optional.of(code));
    }

    private Company stored(CompanyId id) {
        return organizations.findCompany(id).orElseThrow();
    }

    @Test
    void companyAdminCannotRename() {
        Company before = stored(company);

        assertThatThrownBy(() -> update.execute(Actors.companyAdmin(company), company, name("Nuevo")))
                .isInstanceOf(ForbiddenException.class);
        assertThat(stored(company)).isEqualTo(before);
    }

    @Test
    void companyAdminOfOtherCompanyCannotRename() {
        CompanyId other = Fixtures.company(organizations);
        Company before = stored(company);

        assertThatThrownBy(() -> update.execute(Actors.companyAdmin(other), company, name("Nuevo")))
                .isInstanceOf(ForbiddenException.class);
        assertThat(stored(company)).isEqualTo(before);
    }

    @Test
    void groupAdminCanRename() {
        update.execute(Actors.groupAdmin(), company, name("Nuevo"));

        assertThat(stored(company).name()).isEqualTo("Nuevo");
    }

    @Test
    void onlyGroupAdminChangesTheCode() {
        Company before = stored(company);

        assertThatThrownBy(() -> update.execute(Actors.companyAdmin(company), company, code("NUEVO")))
                .isInstanceOf(ForbiddenException.class);
        assertThat(stored(company)).isEqualTo(before);

        update.execute(Actors.groupAdmin(), company, code("NUEVO"));

        assertThat(stored(company).code()).isEqualTo("NUEVO");
        assertThat(stored(company).name()).isEqualTo(before.name());
    }

    @Test
    void newCodeIsValidatedAndUnique() {
        CompanyId other = Fixtures.company(organizations);
        String taken = stored(other).code();

        assertThatThrownBy(() -> update.execute(Actors.groupAdmin(), company, code("abc")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("El código de la empresa tiene de 2 a 6 letras mayúsculas o cifras");
        assertThatThrownBy(() -> update.execute(Actors.groupAdmin(), company, code(taken)))
                .isInstanceOf(ConflictException.class).hasMessage("Ya existe una empresa con ese código");
        assertThat(update.execute(Actors.groupAdmin(), company, code(stored(company).code())).code())
                .isEqualTo(stored(company).code());
    }

    @Test
    void groupAdminChangesTheProjectLabelAndOmittingItKeepsIt() {
        ProjectLabel label = ProjectLabel.of("Comunidad", "Comunidades", true);
        update.execute(Actors.groupAdmin(), company,
                new CompanyChanges(Optional.empty(), Optional.empty(), Optional.of(label)));
        assertThat(stored(company).projectLabel()).isEqualTo(new ProjectLabel("comunidad", "comunidades", true));

        update.execute(Actors.groupAdmin(), company, name("Nuevo"));
        assertThat(stored(company).projectLabel()).isEqualTo(new ProjectLabel("comunidad", "comunidades", true));
    }

    @Test
    void companyAdminCannotChangeTheProjectLabel() {
        Company before = stored(company);

        assertThatThrownBy(() -> update.execute(Actors.companyAdmin(company), company, new CompanyChanges(
                Optional.empty(), Optional.empty(), Optional.of(ProjectLabel.of("obra", "obras", true)))))
                .isInstanceOf(ForbiddenException.class);
        assertThat(stored(company)).isEqualTo(before);
    }

    @Test
    void unknownCompanyIsNotFound() {
        assertThatThrownBy(() -> update.execute(Actors.groupAdmin(), new CompanyId(UUID.randomUUID()), name("X")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void savingMarksTheCodeReviewed() {
        organizations.saveCompany(stored(company).withCodeReviewed(false));
        update.execute(Actors.groupAdmin(), company, name("Nuevo"));
        assertThat(stored(company).codeReviewed()).isTrue();

        organizations.saveCompany(stored(company).withCodeReviewed(false));
        update.execute(Actors.groupAdmin(), company, code("NUEVO"));
        assertThat(stored(company).codeReviewed()).isTrue();
    }
}
