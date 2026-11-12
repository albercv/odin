package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** La tabla no lleva empresa: cada consulta filtra por la empresa del proyecto (join o EXISTS con {@code project}). */
public class PostgresProjectMemberRepository implements ProjectMemberRepository {

    private static final String OF_COMPANY = "EXISTS (SELECT 1 FROM project p WHERE p.id = m.project_id"
            + " AND p.company_id = :company)";
    private final ScopedTransactions tx;

    public PostgresProjectMemberRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public void add(CompanyId companyId, ProjectId projectId, UserId userId) {
        tx.runInScope(CompanyScope.of(companyId), jdbc -> jdbc.sql("""
                INSERT INTO project_member (project_id, user_id)
                SELECT p.id, :user FROM project p WHERE p.id = :project AND p.company_id = :company
                ON CONFLICT (project_id, user_id) DO UPDATE SET removed_at = NULL""")
                .param("project", projectId.value()).param("user", userId.value())
                .param("company", companyId.value()).update());
    }

    @Override
    public void remove(CompanyId companyId, ProjectId projectId, UserId userId) {
        tx.runInScope(CompanyScope.of(companyId), jdbc -> jdbc.sql("UPDATE project_member m SET removed_at = now()"
                + " WHERE m.removed_at IS NULL AND m.project_id = :project AND m.user_id = :user AND " + OF_COMPANY)
                .param("project", projectId.value()).param("user", userId.value())
                .param("company", companyId.value()).update());
    }

    @Override
    public void removeAllOf(CompanyId companyId, UserId userId) {
        tx.runInScope(CompanyScope.of(companyId), jdbc -> jdbc.sql("UPDATE project_member m SET removed_at = now()"
                + " WHERE m.removed_at IS NULL AND m.user_id = :user AND " + OF_COMPANY)
                .param("user", userId.value()).param("company", companyId.value()).update());
    }

    @Override
    public List<UserId> listMembers(CompanyId companyId, ProjectId projectId) {
        return tx.inScope(CompanyScope.of(companyId), jdbc -> jdbc.sql("SELECT m.user_id FROM project_member m"
                + " WHERE m.removed_at IS NULL AND m.project_id = :project AND " + OF_COMPANY + " ORDER BY m.added_at, m.user_id")
                .param("project", projectId.value()).param("company", companyId.value())
                .query((rs, n) -> new UserId(Sql.uuid(rs, "user_id"))).list());
    }

    @Override
    public Set<ProjectId> projectsOf(CompanyId companyId, UserId userId) {
        return projectsOf(companyId, userId, "p.deleted_at IS NULL");
    }

    @Override
    public Set<ProjectId> deletedProjectsOf(CompanyId companyId, UserId userId) {
        return projectsOf(companyId, userId, "p.deleted_at IS NOT NULL");
    }

    private Set<ProjectId> projectsOf(CompanyId companyId, UserId userId, String deletedFilter) {
        String sql = """
                SELECT p.id FROM project_member m JOIN project p ON p.id = m.project_id
                WHERE m.removed_at IS NULL AND m.user_id = :user AND p.company_id = :company AND %s""".formatted(deletedFilter);
        return tx.inScope(CompanyScope.of(companyId), jdbc -> new HashSet<>(jdbc.sql(sql)
                .param("user", userId.value()).param("company", companyId.value())
                .query((rs, n) -> new ProjectId(Sql.uuid(rs, "id"))).list()));
    }
}
