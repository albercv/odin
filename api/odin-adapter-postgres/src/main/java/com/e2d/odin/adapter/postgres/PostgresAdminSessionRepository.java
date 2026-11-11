package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.AdminSessionRepository;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;

public class PostgresAdminSessionRepository implements AdminSessionRepository {

    private static final CompanyScope ALL = CompanyScope.all();
    private final ScopedTransactions tx;

    public PostgresAdminSessionRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public void save(AdminSession s) {
        tx.runInScope(ALL, jdbc -> jdbc.sql("""
                INSERT INTO admin_session (token_hash, user_id, created_at, last_seen_at)
                VALUES (:token, :user, :created, :seen)
                ON CONFLICT (token_hash) DO UPDATE SET last_seen_at = :seen""")
                .param("token", s.tokenHash()).param("user", s.userId().value())
                .param("created", Sql.ts(s.createdAt())).param("seen", Sql.ts(s.lastSeenAt())).update());
    }

    @Override
    public Optional<AdminSession> findByTokenHash(String tokenHash) {
        return tx.inScope(ALL, jdbc -> jdbc.sql("""
                SELECT token_hash, user_id, created_at, last_seen_at FROM admin_session WHERE token_hash = :token""")
                .param("token", tokenHash).query((rs, n) -> new AdminSession(rs.getString("token_hash"),
                        new UserId(Sql.uuid(rs, "user_id")), Sql.instant(rs, "created_at"),
                        Sql.instant(rs, "last_seen_at"))).optional());
    }

    @Override
    public void delete(String tokenHash) {
        tx.runInScope(ALL, jdbc -> jdbc.sql("DELETE FROM admin_session WHERE token_hash = :token")
                .param("token", tokenHash).update());
    }

    @Override
    public void deleteByUser(UserId userId) {
        tx.runInScope(ALL, jdbc -> jdbc.sql("DELETE FROM admin_session WHERE user_id = :user")
                .param("user", userId.value()).update());
    }
}
