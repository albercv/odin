package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.ProjectMemberRepository;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * La tabla no lleva empresa: los casos de uso comprueban antes que el proyecto es de la empresa del administrador.
 * Por eso solo {@code projectsOf} puede filtrar por empresa en el SQL.
 */
public class PostgresProjectMemberRepository implements ProjectMemberRepository {

    private static final CompanyScope ALL = CompanyScope.all();
    private final ScopedTransactions tx;

    public PostgresProjectMemberRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public void add(ProjectId projectId, UserId userId) {
        tx.runInScope(ALL, jdbc -> jdbc.sql("""
                INSERT INTO project_member (project_id, user_id) VALUES (:project, :user)
                ON CONFLICT (project_id, user_id) DO NOTHING""")
                .param("project", projectId.value()).param("user", userId.value()).update());
    }

    @Override
    public void remove(ProjectId projectId, UserId userId) {
        tx.runInScope(ALL, jdbc -> jdbc.sql("DELETE FROM project_member WHERE project_id = :project AND user_id = :user")
                .param("project", projectId.value()).param("user", userId.value()).update());
    }

    @Override
    public List<UserId> listMembers(ProjectId projectId) {
        return tx.inScope(ALL, jdbc -> jdbc.sql("""
                SELECT user_id FROM project_member WHERE project_id = :project ORDER BY added_at, user_id""")
                .param("project", projectId.value()).query((rs, n) -> new UserId(Sql.uuid(rs, "user_id"))).list());
    }

    @Override
    public Set<ProjectId> projectsOf(CompanyId companyId, UserId userId) {
        CompanyScope scope = CompanyScope.of(companyId);
        return tx.inScope(scope, jdbc -> new HashSet<>(Sql.scoped(jdbc.sql("""
                SELECT p.id FROM project_member m JOIN project p ON p.id = m.project_id
                WHERE m.user_id = :user AND p.deleted_at IS NULL AND p.company_id = :company AND """
                + Sql.inScope("p.company_id")), scope).param("user", userId.value())
                .param("company", companyId.value()).query((rs, n) -> new ProjectId(Sql.uuid(rs, "id"))).list()));
    }
}
