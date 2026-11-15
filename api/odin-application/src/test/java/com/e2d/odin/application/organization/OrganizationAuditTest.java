package com.e2d.odin.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryMembershipRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.audit.AuditOutcome;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Una entrada por acción de administración de la organización: quién, sobre qué empresa y qué cambió. */
class OrganizationAuditTest {

    private final InMemoryOrganizationRepository organizations = new InMemoryOrganizationRepository();
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryProjectMemberRepository members = new InMemoryProjectMemberRepository(organizations);
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final Actor group = Actors.groupAdmin();
    private final CompanyId company = Fixtures.company(organizations);
    private final Actor admin = Actors.companyAdmin(company);

    private AuditEntry only(AuditAction action) {
        assertThat(audit.entries).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo(action));
        AuditEntry entry = audit.entries.get(0);
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.DONE);
        return entry;
    }

    @Test
    void createCompanyRecordsTheNewCompany() {
        Company created = new CreateCompany(organizations, new SequenceIdGenerator(), audit)
                .execute(group, "Talleres Demo", "TD");

        AuditEntry e = only(AuditAction.COMPANY_CREATED);
        assertThat(e.actorUserId()).contains(group.userId());
        assertThat(e.actorRole()).contains("GROUP_ADMIN");
        assertThat(e.company()).contains(created.id());
        assertThat(e.target()).contains(new AuditEntry.Target("COMPANY", created.id().value().toString(), "Talleres Demo"));
    }

    @Test
    void updateCompanyRecordsWhichFieldsChanged() {
        new UpdateCompany(organizations, audit).execute(group, company,
                new CompanyChanges(Optional.of("Nuevo nombre"), Optional.empty()));

        AuditEntry e = only(AuditAction.COMPANY_UPDATED);
        assertThat(e.company()).contains(company);
        assertThat(e.target().orElseThrow().label()).isEqualTo("Nuevo nombre");
        assertThat(e.details()).containsEntry("nameChanged", true).containsEntry("codeChanged", false);
    }

    @Test
    void capabilitiesRecordBeforeAndAfterSorted() {
        CompanyId limited = Fixtures.companyWith(organizations,
                EnumSet.of(Capability.UPLOAD_DOCUMENTS, Capability.SEARCH_KNOWLEDGE));

        new SetCompanyCapabilities(organizations, audit).execute(group, limited,
                EnumSet.of(Capability.SEARCH_KNOWLEDGE));

        AuditEntry e = only(AuditAction.COMPANY_CAPABILITIES_CHANGED);
        assertThat(e.company()).contains(limited);
        assertThat(e.details()).containsEntry("before", List.of("SEARCH_KNOWLEDGE", "UPLOAD_DOCUMENTS"))
                .containsEntry("after", List.of("SEARCH_KNOWLEDGE"));
    }

    @Test
    void createProjectLeavesTheCompanyAdminsCompany() {
        Project project = new CreateProject(organizations, new SequenceIdGenerator(), audit)
                .execute(admin, company, "Obra Norte");

        AuditEntry e = only(AuditAction.PROJECT_CREATED);
        assertThat(e.actorRole()).contains("COMPANY_ADMIN");
        assertThat(e.company()).contains(company);
        assertThat(e.project()).contains(project.id());
        assertThat(e.target()).contains(new AuditEntry.Target("PROJECT", project.id().value().toString(), "Obra Norte"));
    }

    @Test
    void updateProjectRecordsRestrictedAndRename() {
        ProjectId id = Fixtures.project(organizations, company);

        new UpdateProject(organizations, audit).execute(admin, company, id,
                new ProjectChanges(Optional.of("Otra obra"), Optional.of(false)));

        AuditEntry e = only(AuditAction.PROJECT_UPDATED);
        assertThat(e.company()).contains(company);
        assertThat(e.project()).contains(id);
        assertThat(e.details()).containsEntry("nameChanged", true).containsEntry("restricted", false);
    }

    @Test
    void deleteProjectRecordsItsName() {
        ProjectId id = Fixtures.project(organizations, company);

        new DeleteProject(organizations, new MutableClock(), audit).execute(admin, company, id);

        AuditEntry e = only(AuditAction.PROJECT_DELETED);
        assertThat(e.company()).contains(company);
        assertThat(e.target().orElseThrow().label()).isEqualTo("Proyecto");
    }

    @Test
    void projectMembersAreRecordedOnAddAndRemove() {
        ProjectId id = Fixtures.project(organizations, company);
        UserId worker = Fixtures.person(users, memberships, "Wanda", Role.WORKER, company, true);

        new AddProjectMember(organizations, memberships, members, audit).execute(admin, company, id, worker);
        new RemoveProjectMember(organizations, members, audit).execute(admin, company, id, worker);

        assertThat(audit.entries).extracting(AuditEntry::action)
                .containsExactly(AuditAction.PROJECT_MEMBER_ADDED, AuditAction.PROJECT_MEMBER_REMOVED);
        assertThat(audit.entries).allSatisfy(e -> {
            assertThat(e.company()).contains(company);
            assertThat(e.project()).contains(id);
            assertThat(e.target()).contains(new AuditEntry.Target("USER", worker.value().toString(), null));
        });
    }

    @Test
    void refusedActionsLeaveNoEntry() {
        Set<Capability> all = EnumSet.allOf(Capability.class);
        assertThatThrownBy(() -> new SetCompanyCapabilities(organizations, audit).execute(admin, company, all))
                .isInstanceOf(ForbiddenException.class);
        assertThat(audit.entries).isEmpty();
    }
}
