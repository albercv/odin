package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class PostgresMembershipRepository implements MembershipRepository {

    private static final String SELECT =
            "SELECT user_id, company_id, is_company_admin, is_worker, active FROM membership ";
    private static final CompanyScope ALL = CompanyScope.all();
    private final ScopedTransactions tx;

    public PostgresMembershipRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public void save(Membership m) {
        tx.runInScope(CompanyScope.of(m.companyId()), jdbc -> jdbc.sql("""
                INSERT INTO membership (user_id, company_id, is_company_admin, is_worker, active)
                VALUES (:user, :company, :admin, :worker, :active)
                ON CONFLICT (user_id, company_id) DO UPDATE SET is_company_admin = :admin, is_worker = :worker,
                    active = :active, deleted_at = NULL""")
                .param("user", m.userId().value()).param("company", m.companyId().value())
                .param("admin", m.isCompanyAdmin()).param("worker", m.isWorker()).param("active", m.active())
                .update());
    }

    @Override
    public Optional<Membership> find(UserId userId, CompanyId companyId) {
        return tx.inScope(CompanyScope.of(companyId), jdbc -> jdbc.sql(SELECT + """
                WHERE user_id = :user AND company_id = :company AND deleted_at IS NULL""")
                .param("user", userId.value()).param("company", companyId.value())
                .query((rs, n) -> membership(rs)).optional());
    }

    @Override
    public List<Membership> listByCompany(CompanyId companyId) {
        return tx.inScope(CompanyScope.of(companyId), jdbc -> jdbc.sql(SELECT + """
                WHERE company_id = :company AND deleted_at IS NULL ORDER BY seq""")
                .param("company", companyId.value()).query((rs, n) -> membership(rs)).list());
    }

    /** Recorre varias empresas a la vez: por eso lee con alcance de grupo y filtra por persona en el SQL. */
    @Override
    public List<Membership> listByUser(UserId userId) {
        return tx.inScope(ALL, jdbc -> jdbc.sql(SELECT + """
                WHERE user_id = :user AND deleted_at IS NULL ORDER BY seq""")
                .param("user", userId.value()).query((rs, n) -> membership(rs)).list());
    }

    @Override
    public void delete(UserId userId, CompanyId companyId, Instant at) {
        tx.runInScope(CompanyScope.of(companyId), jdbc -> jdbc.sql("""
                UPDATE membership SET deleted_at = :at
                WHERE user_id = :user AND company_id = :company AND deleted_at IS NULL""")
                .param("at", Sql.ts(at)).param("user", userId.value()).param("company", companyId.value())
                .update());
    }

    private static Membership membership(ResultSet rs) throws SQLException {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (rs.getBoolean("is_company_admin")) {
            roles.add(Role.COMPANY_ADMIN);
        }
        if (rs.getBoolean("is_worker")) {
            roles.add(Role.WORKER);
        }
        return new Membership(new UserId(Sql.uuid(rs, "user_id")), new CompanyId(Sql.uuid(rs, "company_id")), roles,
                rs.getBoolean("active"));
    }
}
