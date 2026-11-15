package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.AdminChatKeyRepository;
import com.e2d.odin.domain.identity.AdminChatKey;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Como las claves de trabajador: se lee y se escribe con ámbito de grupo porque se resuelve antes de tener ámbito. */
public class PostgresAdminChatKeyRepository implements AdminChatKeyRepository {

    private static final String COLUMNS = "id, user_id, company_id, prefix, token_hash, created_at, expires_at, revoked_at";
    private static final CompanyScope ALL = CompanyScope.all();
    private final ScopedTransactions tx;

    public PostgresAdminChatKeyRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public void save(AdminChatKey k) {
        tx.runInScope(ALL, jdbc -> jdbc.sql("""
                INSERT INTO admin_chat_key (id, user_id, company_id, prefix, token_hash, created_at, expires_at,
                    revoked_at)
                VALUES (:id, :user, :company, :prefix, :hash, :created, :expires, :revoked)
                ON CONFLICT (id) DO UPDATE SET revoked_at = :revoked""")
                .param("id", k.id()).param("user", k.userId().value())
                .param("company", k.companyId().map(CompanyId::value).orElse(null), java.sql.Types.OTHER)
                .param("prefix", k.prefix()).param("hash", k.tokenHash()).param("created", Sql.ts(k.createdAt()))
                .param("expires", Sql.ts(k.expiresAt())).param("revoked", Sql.ts(k.revokedAt())).update());
    }

    @Override
    public Optional<AdminChatKey> findById(UUID id) {
        return tx.inScope(ALL, jdbc -> jdbc.sql("SELECT " + COLUMNS + " FROM admin_chat_key WHERE id = :id")
                .param("id", id).query((rs, n) -> key(rs)).optional());
    }

    @Override
    public Optional<AdminChatKey> findByPrefix(String prefix) {
        return tx.inScope(ALL, jdbc -> jdbc.sql("SELECT " + COLUMNS + " FROM admin_chat_key WHERE prefix = :prefix")
                .param("prefix", prefix).query((rs, n) -> key(rs)).optional());
    }

    @Override
    public List<AdminChatKey> listActiveOf(UserId userId, Instant now) {
        return tx.inScope(ALL, jdbc -> jdbc.sql("SELECT " + COLUMNS + " FROM admin_chat_key WHERE user_id = :user "
                + "AND revoked_at IS NULL AND expires_at > :now ORDER BY created_at DESC")
                .param("user", userId.value()).param("now", Sql.ts(now)).query((rs, n) -> key(rs)).list());
    }

    private static AdminChatKey key(ResultSet rs) throws SQLException {
        return new AdminChatKey(Sql.uuid(rs, "id"), new UserId(Sql.uuid(rs, "user_id")),
                Optional.ofNullable(Sql.uuid(rs, "company_id")).map(CompanyId::new), rs.getString("prefix"),
                rs.getString("token_hash"), Sql.instant(rs, "created_at"), Sql.instant(rs, "expires_at"),
                Sql.optionalInstant(rs, "revoked_at"));
    }
}
