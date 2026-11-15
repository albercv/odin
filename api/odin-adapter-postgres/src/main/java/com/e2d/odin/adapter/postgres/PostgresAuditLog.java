package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.AuditContext;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.audit.AuditEntry.Target;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Escribe por {@code audit_append}: la base toma el bloqueo, enlaza la huella y respeta el interruptor. */
public class PostgresAuditLog implements AuditLog {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String APPEND = """
            SELECT audit_append(:company, :project, :kind, :user, :role, :name, :channel, :via, :action, :targetKind,
                :targetId, :targetLabel, :outcome, CAST(:details AS jsonb), :requestId)""";

    private final JdbcClient jdbc;
    private final TransactionTemplate ownTransaction;
    private final AuditContext context;

    public PostgresAuditLog(DataSource dataSource, AuditContext context) {
        this.jdbc = JdbcClient.create(dataSource);
        this.ownTransaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.context = context;
    }

    @Override
    public void record(AuditEntry entry) {
        append(entry);
    }

    @Override
    public void recordDenied(AuditEntry entry) {
        ownTransaction.executeWithoutResult(status -> append(entry));
    }

    private void append(AuditEntry e) {
        AuditContext.AuditRequest request = context.current();
        jdbc.sql(APPEND)
                .param("company", e.company().map(c -> c.value()).orElse(null), java.sql.Types.OTHER)
                .param("project", e.project().map(p -> p.value()).orElse(null), java.sql.Types.OTHER)
                .param("kind", e.actorKind().name())
                .param("user", e.actorUserId().map(u -> u.value()).orElse(null), java.sql.Types.OTHER)
                .param("role", e.actorRole().orElse(null))
                .param("name", e.actorName().orElse(null))
                .param("channel", request.channel().name())
                .param("via", e.via().orElse(null))
                .param("action", e.action().name())
                .param("targetKind", e.target().map(Target::kind).orElse(null))
                .param("targetId", e.target().map(Target::id).orElse(null))
                .param("targetLabel", e.target().map(Target::label).orElse(null))
                .param("outcome", e.outcome().name())
                .param("details", JSON.writeValueAsString(e.details()))
                .param("requestId", request.requestId().orElse(null))
                .query().singleRow();
    }
}
