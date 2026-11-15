package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.AuditReader;
import com.e2d.odin.domain.audit.AuditActorKind;
import com.e2d.odin.domain.audit.AuditChannel;
import com.e2d.odin.domain.audit.AuditCursor;
import com.e2d.odin.domain.audit.AuditFilter;
import com.e2d.odin.domain.audit.AuditOutcome;
import com.e2d.odin.domain.audit.AuditRow;
import com.e2d.odin.domain.audit.ChainHead;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** Lee por la RLS de audit_event y además filtra la empresa en el SQL; la cadena se lee con el ámbito del grupo. */
public class PostgresAuditReader implements AuditReader {

    private static final String FIND = """
            SELECT e.id, e.occurred_at, e.company_id, e.project_id, e.actor_kind, e.actor_user_id, a.display_name,
                   e.actor_role, e.channel, e.via, e.action, e.target_kind, e.target_id, e.target_label, e.outcome,
                   CAST(e.details AS text) AS details
              FROM audit_event e LEFT JOIN audit_actor a ON a.user_id = e.actor_user_id
             WHERE %s
               AND (CAST(:from AS timestamptz) IS NULL OR e.occurred_at >= CAST(:from AS timestamptz))
               AND (CAST(:to AS timestamptz) IS NULL OR e.occurred_at < CAST(:to AS timestamptz))
               AND (CAST(:company AS uuid) IS NULL OR e.company_id = CAST(:company AS uuid))
               AND (CAST(:project AS uuid) IS NULL OR e.project_id = CAST(:project AS uuid))
               AND (CAST(:action AS text) IS NULL OR e.action = :action)
               AND (CAST(:actor AS uuid) IS NULL OR e.actor_user_id = CAST(:actor AS uuid))
               AND (CAST(:channel AS text) IS NULL OR e.channel = :channel)
               AND (CAST(:outcome AS text) IS NULL OR e.outcome = :outcome)
               AND (CAST(:curAt AS timestamptz) IS NULL
                    OR (e.occurred_at, e.id) < (CAST(:curAt AS timestamptz), CAST(:curId AS bigint)))
             ORDER BY e.occurred_at DESC, e.id DESC
             LIMIT :limit""".formatted(Sql.inScope("e.company_id"));

    private final ScopedTransactions tx;

    public PostgresAuditReader(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public List<AuditRow> find(CompanyScope scope, AuditFilter f, Optional<AuditCursor> after, int limit) {
        return tx.inScope(scope, jdbc -> Sql.scoped(jdbc.sql(FIND), scope)
                .param("from", Sql.ts(f.from())).param("to", Sql.ts(f.to()))
                .param("company", f.company().map(c -> c.value().toString()).orElse(null))
                .param("project", f.project().map(p -> p.value().toString()).orElse(null))
                .param("action", f.action().map(Enum::name).orElse(null))
                .param("actor", f.actor().map(u -> u.value().toString()).orElse(null))
                .param("channel", f.channel().map(Enum::name).orElse(null))
                .param("outcome", f.outcome().map(Enum::name).orElse(null))
                .param("curAt", Sql.ts(after.map(AuditCursor::occurredAt).orElse(null)))
                .param("curId", after.map(AuditCursor::id).orElse(null))
                .param("limit", limit)
                .query((rs, n) -> row(rs)).list());
    }

    private static AuditRow row(ResultSet rs) throws SQLException {
        return new AuditRow(rs.getLong("id"), Sql.instant(rs, "occurred_at"),
                Optional.ofNullable(Sql.uuid(rs, "company_id")).map(CompanyId::new),
                Optional.ofNullable(Sql.uuid(rs, "project_id")).map(ProjectId::new),
                AuditActorKind.valueOf(rs.getString("actor_kind")),
                Optional.ofNullable(Sql.uuid(rs, "actor_user_id")).map(UserId::new),
                Optional.ofNullable(rs.getString("display_name")), Optional.ofNullable(rs.getString("actor_role")),
                AuditChannel.valueOf(rs.getString("channel")), Optional.ofNullable(rs.getString("via")),
                rs.getString("action"), Optional.ofNullable(rs.getString("target_kind")),
                Optional.ofNullable(rs.getString("target_id")), Optional.ofNullable(rs.getString("target_label")),
                AuditOutcome.valueOf(rs.getString("outcome")), rs.getString("details"));
    }

    @Override
    public Optional<ChainHead> head() {
        return tx.inScope(CompanyScope.all(), jdbc -> jdbc.sql("""
                SELECT h.id, h.hash, e.occurred_at FROM audit_chain_head() h JOIN audit_event e ON e.id = h.id""")
                .query((rs, n) -> new ChainHead(rs.getLong("id"), rs.getString("hash"),
                        Sql.instant(rs, "occurred_at"))).optional());
    }

    @Override
    public OptionalLong firstBroken() {
        return tx.inScope(CompanyScope.all(), jdbc -> jdbc.sql("SELECT audit_verify()").query((rs, n) -> {
            long id = rs.getLong(1);
            return rs.wasNull() ? OptionalLong.empty() : OptionalLong.of(id);
        }).single());
    }

    @Override
    public boolean sealMatches(long id, String hash) {
        return tx.inScope(CompanyScope.all(), jdbc -> jdbc.sql("SELECT audit_seal_matches(:id, :hash)")
                .param("id", id).param("hash", hash).query(Boolean.class).single());
    }
}
