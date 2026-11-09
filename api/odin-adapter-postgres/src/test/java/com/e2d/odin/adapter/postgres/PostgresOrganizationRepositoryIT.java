package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Group;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.GroupId;
import com.e2d.odin.domain.shared.ProjectId;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresOrganizationRepositoryIT extends AbstractPostgresIT {

    @Test
    void savesAndReplacesTheGroup() {
        assertThat(orgs.findGroup()).isEmpty();
        orgs.saveGroup(new Group(new GroupId(UUID.randomUUID()), "Grupo"));
        Group renamed = new Group(new GroupId(UUID.randomUUID()), "Grupo 2");
        orgs.saveGroup(renamed);
        assertThat(orgs.findGroup()).contains(renamed);
    }

    @Test
    void databaseRejectsASecondGroupRow() {
        orgs.saveGroup(new Group(new GroupId(UUID.randomUUID()), "Grupo"));
        assertThatThrownBy(() -> {
            try (Connection c = Db.owner().getConnection(); Statement s = c.createStatement()) {
                s.execute("INSERT INTO odin_group (id, name, singleton) VALUES (gen_random_uuid(), 'otro', false)");
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void savesCompanyWithCapabilitiesAndUpdatesThem() {
        CompanyId id = newCompany();
        Company updated = new Company(id, "Nueva", Set.of(Capability.SAVE_KNOWLEDGE_CARD, Capability.SEARCH_KNOWLEDGE));
        orgs.saveCompany(updated);
        assertThat(orgs.findCompany(id)).contains(updated);
        orgs.saveCompany(updated.withCapabilities(Set.of()));
        assertThat(orgs.findCompany(id).orElseThrow().capabilities()).isEmpty();
    }

    @Test
    void listCompaniesHonoursScopeAndInsertionOrder() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        CompanyId c = newCompany();
        assertThat(orgs.listCompanies(CompanyScope.all())).extracting(Company::id).containsExactly(a, b, c);
        assertThat(orgs.listCompanies(CompanyScope.of(b))).extracting(Company::id).containsExactly(b);
        assertThat(orgs.listCompanies(new CompanyScope(Set.of(a, c), false))).extracting(Company::id)
                .containsExactly(a, c);
    }

    @Test
    void projectsAreScopedToTheirCompany() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Project p1 = new Project(new ProjectId(UUID.randomUUID()), a, "P1");
        Project p2 = new Project(new ProjectId(UUID.randomUUID()), a, "P2");
        orgs.saveProject(p1);
        orgs.saveProject(p2);
        orgs.saveProject(new Project(new ProjectId(UUID.randomUUID()), b, "Otro"));
        assertThat(orgs.listProjects(a)).containsExactly(p1, p2);
        assertThat(orgs.findProject(a, p1.id())).contains(p1);
        assertThat(orgs.findProject(b, p1.id())).isEmpty();
    }
}
