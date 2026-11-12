package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateCompanyTest {

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final CreateCompany create = new CreateCompany(organizations, new SequenceIdGenerator());

    @Test
    void onlyGroupAdmin() {
        assertThatThrownBy(() -> create.execute(Actors.companyAdmin(new CompanyId(UUID.randomUUID())), "Nueva", "NUE"))
                .isInstanceOf(ForbiddenException.class);
        assertThat(organizations.listCompanies(Actors.groupAdmin().scope())).isEmpty();
    }

    @Test
    void enablesAllCapabilities() {
        Company company = create.execute(Actors.groupAdmin(), "Talleres Demo", "TD");

        assertThat(company.capabilities()).isEqualTo(EnumSet.allOf(Capability.class));
        assertThat(company.code()).isEqualTo("TD");
        assertThat(organizations.findCompany(company.id())).contains(company);
    }

    @Test
    void codeIsRequiredAndValidated() {
        for (String code : new String[] {null, "", " ", "A", "ABCDEFG", "ab", "A-B", "ÁB"}) {
            assertThatThrownBy(() -> create.execute(Actors.groupAdmin(), "Nueva", code))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("El código de la empresa tiene de 2 a 6 letras mayúsculas o cifras");
        }
        assertThat(create.execute(Actors.groupAdmin(), "Una", "A1").code()).isEqualTo("A1");
        assertThat(create.execute(Actors.groupAdmin(), "Otra", "ABC123").code()).isEqualTo("ABC123");
        assertThat(organizations.listCompanies(Actors.groupAdmin().scope())).hasSize(2);
    }

    @Test
    void duplicateCodeIsRejected() {
        create.execute(Actors.groupAdmin(), "Talleres Demo", "TD");

        assertThatThrownBy(() -> create.execute(Actors.groupAdmin(), "Otra", "TD"))
                .isInstanceOf(ConflictException.class).hasMessage("Ya existe una empresa con ese código");
        assertThat(organizations.listCompanies(Actors.groupAdmin().scope())).hasSize(1);
    }
}
