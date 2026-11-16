package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.AdminSessionRepository;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PostgresAdminSessionRepository implements AdminSessionRepository {

    private static final CompanyScope ALL = CompanyScope.all();
    private final ScopedTransactions tx;

    public PostgresAdminSessionRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    private static final String COLUMNS =
            "id, token_hash, user_id, created_at, last_seen_at, user_agent, ip, reauthenticated_at";

    @Override
    public void save(AdminSession s) {
        tx.runInScope(ALL, jdbc -> jdbc.sql("""
                INSERT INTO admin_session (id, token_hash, user_id, created_at, last_seen_at, user_agent, ip,
                    reauthenticated_at)
                VALUES (:id, :token, :user, :created, :seen, :agent, :ip, :reauth)
                ON CONFLICT (token_hash) DO UPDATE SET last_seen_at = :seen""")
                .param("id", s.id()).param("token", s.tokenHash()).param("user", s.userId().value())
                .param("created", Sql.ts(s.createdAt())).param("seen", Sql.ts(s.lastSeenAt()))
                .param("agent", s.userAgent().orElse(null)).param("ip", s.ip().orElse(null))
                .param("reauth", Sql.ts(s.reauthenticatedAt())).update());
    }

    @Override
    public Optional<AdminSession> findByTokenHash(String tokenHash) {
        return tx.inScope(ALL, jdbc -> jdbc.sql("SELECT " + COLUMNS + " FROM admin_session WHERE token_hash = :token")
                .param("token", tokenHash).query(PostgresAdminSessionRepository::map).optional());
    }

    @Override
    public List<AdminSession> findByUser(UserId userId) {
        return tx.inScope(ALL, jdbc -> jdbc.sql("SELECT " + COLUMNS + " FROM admin_session WHERE user_id = :user")
                .param("user", userId.value()).query(PostgresAdminSessionRepository::map).list());
    }

    private static AdminSession map(ResultSet rs, int n) throws SQLException {
        return new AdminSession(Sql.uuid(rs, "id"), rs.getString("token_hash"), new UserId(Sql.uuid(rs, "user_id")),
                Sql.instant(rs, "created_at"), Sql.instant(rs, "last_seen_at"),
                Optional.ofNullable(rs.getString("user_agent")), Optional.ofNullable(rs.getString("ip")),
                Sql.instant(rs, "reauthenticated_at"));
    }

    @Override
    public void delete(String tokenHash) {
        tx.runInScope(ALL, jdbc -> jdbc.sql("DELETE FROM admin_session WHERE token_hash = :token")
                .param("token", tokenHash).update());
    }

    @Override
    public int deleteByIdAndUser(UUID id, UserId userId) {
        return tx.inScope(ALL, jdbc -> jdbc.sql("DELETE FROM admin_session WHERE id = :id AND user_id = :user")
                .param("id", id).param("user", userId.value()).update());
    }

    @Override
    public int deleteByUser(UserId userId) {
        return tx.inScope(ALL, jdbc -> jdbc.sql("DELETE FROM admin_session WHERE user_id = :user")
                .param("user", userId.value()).update());
    }
}
