package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.AuditSettingsRepository;
import com.e2d.odin.domain.audit.AuditSettings;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.UserId;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Lee con el ámbito del grupo (la RLS de audit_setting) y cambia solo por {@code audit_update_settings}. */
public class PostgresAuditSettings implements AuditSettingsRepository {

    private final ScopedTransactions tx;

    public PostgresAuditSettings(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public AuditSettings get() {
        return tx.inScope(CompanyScope.all(), jdbc -> jdbc.sql("""
                SELECT enabled, usage_policy_reference, chat_enabled, updated_by, updated_at FROM audit_setting""")
                .query((rs, n) -> new AuditSettings(rs.getBoolean("enabled"),
                        Optional.ofNullable(rs.getString("usage_policy_reference")), rs.getBoolean("chat_enabled"),
                        Optional.ofNullable(rs.getObject("updated_by", UUID.class)).map(UserId::new),
                        Optional.ofNullable(rs.getObject("updated_at", OffsetDateTime.class))
                                .map(OffsetDateTime::toInstant)))
                .single());
    }

    @Override
    public void update(boolean enabled, Optional<String> usagePolicyReference, boolean chatEnabled, UserId by) {
        tx.runInScope(CompanyScope.all(), jdbc -> jdbc.sql("SELECT audit_update_settings(:enabled, :reference, :chat, :by)")
                .param("enabled", enabled).param("reference", usagePolicyReference.orElse(null))
                .param("chat", chatEnabled).param("by", by.value()).query().singleRow());
    }
}
