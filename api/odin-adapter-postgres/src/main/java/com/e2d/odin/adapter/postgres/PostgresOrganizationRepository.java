package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.organization.Company;
import com.e2d.odin.domain.organization.Group;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.GroupId;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class PostgresOrganizationRepository implements OrganizationRepository {

    private static final CompanyScope ALL = CompanyScope.all();
    private final ScopedTransactions tx;

    public PostgresOrganizationRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public Optional<Group> findGroup() {
        return tx.inScope(ALL, jdbc -> jdbc.sql("SELECT id, name FROM odin_group")
                .query((rs, n) -> new Group(new GroupId(Sql.uuid(rs, "id")), rs.getString("name"))).optional());
    }

    @Override
    public void saveGroup(Group g) {
        tx.runInScope(ALL, jdbc -> jdbc.sql("""
                INSERT INTO odin_group (id, name) VALUES (:id, :name)
                ON CONFLICT (singleton) DO UPDATE SET id = :id, name = :name""")
                .param("id", g.id().value()).param("name", g.name()).update());
    }

    @Override
    public void saveCompany(Company c) {
        tx.runInScope(ALL, jdbc -> {
            jdbc.sql("INSERT INTO company (id, name) VALUES (:id, :name) ON CONFLICT (id) DO UPDATE SET name = :name")
                    .param("id", c.id().value()).param("name", c.name()).update();
            jdbc.sql("DELETE FROM company_capability WHERE company_id = :id").param("id", c.id().value()).update();
            for (Capability capability : c.capabilities()) {
                jdbc.sql("INSERT INTO company_capability (company_id, capability) VALUES (:id, :capability)")
                        .param("id", c.id().value()).param("capability", capability.name()).update();
            }
        });
    }

    @Override
    public Optional<Company> findCompany(CompanyId id) {
        return tx.inScope(ALL, jdbc -> jdbc.sql("SELECT id, name FROM company WHERE id = :id")
                .param("id", id.value()).query((rs, n) -> company(jdbc, rs.getObject("id", UUID.class),
                        rs.getString("name"))).optional());
    }

    @Override
    public List<Company> listCompanies(CompanyScope scope) {
        return tx.inScope(scope, jdbc -> jdbc.sql("SELECT id, name FROM company ORDER BY seq")
                .query((rs, n) -> company(jdbc, rs.getObject("id", UUID.class), rs.getString("name"))).list());
    }

    @Override
    public void saveProject(Project p) {
        tx.runInScope(CompanyScope.of(p.companyId()), jdbc -> jdbc.sql("""
                INSERT INTO project (id, company_id, name, restricted) VALUES (:id, :company, :name, :restricted)
                ON CONFLICT (id) DO UPDATE SET name = :name, restricted = :restricted""")
                .param("id", p.id().value()).param("company", p.companyId().value()).param("name", p.name())
                .param("restricted", p.restricted()).update());
    }

    @Override
    public Optional<Project> findProject(CompanyId companyId, ProjectId id) {
        return tx.inScope(CompanyScope.of(companyId), jdbc -> jdbc
                .sql("SELECT id, company_id, name, restricted FROM project WHERE id = :id AND company_id = :company AND deleted_at IS NULL")
                .param("id", id.value()).param("company", companyId.value())
                .query((rs, n) -> project(rs)).optional());
    }

    @Override
    public List<Project> listProjects(CompanyId companyId) {
        return tx.inScope(CompanyScope.of(companyId), jdbc -> jdbc
                .sql("SELECT id, company_id, name, restricted FROM project WHERE company_id = :company AND deleted_at IS NULL ORDER BY seq")
                .param("company", companyId.value()).query((rs, n) -> project(rs)).list());
    }

    private static Company company(JdbcClient jdbc, UUID id, String name) {
        Set<Capability> capabilities = EnumSet.noneOf(Capability.class);
        jdbc.sql("SELECT capability FROM company_capability WHERE company_id = :id").param("id", id)
                .query(String.class).list().forEach(c -> capabilities.add(Capability.valueOf(c)));
        return new Company(new CompanyId(id), name, capabilities);
    }

    private static Project project(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Project(new ProjectId(Sql.uuid(rs, "id")), new CompanyId(Sql.uuid(rs, "company_id")),
                rs.getString("name"), rs.getBoolean("restricted"));
    }

    @Override
    public void deleteProject(CompanyId companyId, ProjectId id, Instant at) {
        tx.runInScope(CompanyScope.of(companyId), jdbc -> jdbc.sql("""
                UPDATE project SET deleted_at = :at
                WHERE id = :id AND company_id = :company AND deleted_at IS NULL""")
                .param("at", Sql.ts(at)).param("id", id.value()).param("company", companyId.value()).update());
    }
}
