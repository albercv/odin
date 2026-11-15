package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateProjectTest {

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final UpdateProject update = new UpdateProject(organizations, audit);
    private final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(organizations);
    private final CompanyId company = Fixtures.company(organizations);
    private final CreateProject create = new CreateProject(organizations, new SequenceIdGenerator(), audit);
    private final Project obra = create.execute(Actors.companyAdmin(company), company, "Obra");

    private Project rename(Actor actor, CompanyId companyId, String name) {
        return update.execute(actor, companyId, obra.id(), new ProjectChanges(Optional.of(name), Optional.empty()));
    }

    private Project restrict(boolean restricted) {
        return update.execute(Actors.companyAdmin(company), company, obra.id(),
                new ProjectChanges(Optional.empty(), Optional.of(restricted)));
    }

    @Test
    void renames() {
        Project renamed = rename(Actors.companyAdmin(company), company, "  Reforma ");

        assertThat(renamed.name()).isEqualTo("Reforma");
        assertThat(organizations.findProject(company, obra.id())).contains(renamed);
    }

    @Test
    void duplicateNameIsConflict() {
        create.execute(Actors.companyAdmin(company), company, "Reforma");

        assertThatThrownBy(() -> rename(Actors.companyAdmin(company), company, "reforma"))
                .isInstanceOf(ConflictException.class).hasMessage("Ya existe un proyecto con ese nombre");
        assertThat(organizations.findProject(company, obra.id()).orElseThrow().name()).isEqualTo("Obra");
    }

    @Test
    void sameNameIsNotConflict() {
        Project renamed = rename(Actors.companyAdmin(company), company, "OBRA");

        assertThat(renamed.name()).isEqualTo("OBRA");
    }

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> rename(Actors.companyAdmin(company), company, "  "))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void projectOfAnotherCompanyIsNotFound() {
        CompanyId other = Fixtures.company(organizations);

        assertThatThrownBy(() -> rename(Actors.companyAdmin(other), other, "Reforma"))
                .isInstanceOf(NotFoundException.class);
        assertThat(organizations.findProject(company, obra.id()).orElseThrow().name()).isEqualTo("Obra");
    }

    @Test
    void adminOfAnotherCompanyIsForbidden() {
        CompanyId stranger = new CompanyId(UUID.randomUUID());

        assertThatThrownBy(() -> rename(Actors.companyAdmin(stranger), company, "Reforma"))
                .isInstanceOf(ForbiddenException.class);
        assertThat(organizations.findProject(company, obra.id()).orElseThrow().name()).isEqualTo("Obra");
    }

    @Test
    void marksCompanyWideAndClosesAgain() {
        assertThat(obra.restricted()).isTrue();

        assertThat(restrict(false).restricted()).isFalse();
        assertThat(organizations.findProject(company, obra.id()).orElseThrow().restricted()).isFalse();
        assertThat(restrict(true).restricted()).isTrue();
        assertThat(organizations.findProject(company, obra.id()).orElseThrow().restricted()).isTrue();
    }

    @Test
    void companyWideKeepsMembers() {
        UserId wanda = new UserId(UUID.randomUUID());
        members.add(obra.id(), wanda);

        restrict(false);
        assertThat(members.listMembers(obra.id())).containsExactly(wanda);
        restrict(true);
        assertThat(members.listMembers(obra.id())).containsExactly(wanda);
    }

    @Test
    void changingOnlyTheNameKeepsRestriction() {
        restrict(false);

        assertThat(rename(Actors.companyAdmin(company), company, "Reforma").restricted()).isFalse();
    }

    @Test
    void adminOfAnotherCompanyCannotChangeRestriction() {
        CompanyId stranger = new CompanyId(UUID.randomUUID());

        assertThatThrownBy(() -> update.execute(Actors.companyAdmin(stranger), company, obra.id(),
                new ProjectChanges(Optional.empty(), Optional.of(false)))).isInstanceOf(ForbiddenException.class);
        assertThat(organizations.findProject(company, obra.id()).orElseThrow().restricted()).isTrue();
    }
}
