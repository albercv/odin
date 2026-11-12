package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class PostgresAccessKeyRepository implements AccessKeyRepository {

    private static final String COLUMNS =
            "id, user_id, company_id, label, prefix, secret_hash, created_at, revoked_at";
    private static final CompanyScope ALL = CompanyScope.all();
    private final ScopedTransactions tx;

    public PostgresAccessKeyRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public void save(AccessKey k) {
        tx.runInScope(ALL, jdbc -> jdbc.sql("""
                INSERT INTO access_key (id, user_id, company_id, label, prefix, secret_hash, created_at, revoked_at)
                VALUES (:id, :user, :company, :label, :prefix, :secret, :created, :revoked)
                ON CONFLICT (id) DO UPDATE SET label = :label, prefix = :prefix, secret_hash = :secret,
                    revoked_at = :revoked""")
                .param("id", k.id().value()).param("user", k.userId().value()).param("company", k.companyId().value())
                .param("label", k.label()).param("prefix", k.prefix()).param("secret", k.secretHash())
                .param("created", Sql.ts(k.createdAt()))
                .param("revoked", Sql.ts(k.revokedAt())).update());
    }

    @Override
    public Optional<AccessKey> findById(AccessKeyId id) {
        return tx.inScope(ALL, jdbc -> jdbc.sql("SELECT " + COLUMNS + " FROM access_key WHERE id = :id")
                .param("id", id.value()).query((rs, n) -> key(rs)).optional());
    }

    @Override
    public Optional<AccessKey> findByPrefix(String prefix) {
        return tx.inScope(ALL, jdbc -> jdbc.sql("SELECT " + COLUMNS + " FROM access_key WHERE prefix = :prefix")
                .param("prefix", prefix).query((rs, n) -> key(rs)).optional());
    }

    @Override
    public List<AccessKey> listByMembership(UserId userId, CompanyId companyId) {
        return tx.inScope(new CompanyScope(Set.of(companyId), false), jdbc -> jdbc.sql("SELECT " + COLUMNS
                + " FROM access_key WHERE user_id = :user AND company_id = :company ORDER BY seq")
                .param("user", userId.value()).param("company", companyId.value()).query((rs, n) -> key(rs)).list());
    }

    private static AccessKey key(ResultSet rs) throws SQLException {
        return new AccessKey(new AccessKeyId(Sql.uuid(rs, "id")), new UserId(Sql.uuid(rs, "user_id")),
                new CompanyId(Sql.uuid(rs, "company_id")), rs.getString("label"), rs.getString("prefix"),
                rs.getString("secret_hash"), Sql.instant(rs, "created_at"), Sql.optionalInstant(rs, "revoked_at"));
    }
}
