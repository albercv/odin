package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.port.AuditContext;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditChannel;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class PostgresAuditLogIT extends AbstractPostgresIT {

    private static final String ZEROS = "0".repeat(64);
    private static final AuditContext PANEL = () -> new AuditContext.AuditRequest(AuditChannel.PANEL,
            Optional.of("req-1"), Optional.empty(), Optional.empty());

    private final PostgresAuditLog log = new PostgresAuditLog(Db.app(), PANEL);
    private final JdbcClient owner = JdbcClient.create(Db.owner());
    private final JdbcClient app = JdbcClient.create(Db.app());

    @BeforeEach
    void switchOn() {
        owner.sql("UPDATE audit_setting SET enabled = true").update();
    }

    private static AuditEntry entry() {
        return AuditEntry.bySystem(AuditAction.AUDIT_ENABLED);
    }

    private long rows() {
        return owner.sql("SELECT count(*) FROM audit_event").query(Long.class).single();
    }

    private Long verify() {
        return owner.sql("SELECT audit_verify()").query((rs, n) -> (Long) rs.getObject(1)).list().get(0);
    }

    @Test
    void recordLeavesOneRowWithItsFields() {
        CompanyId company = newCompany();
        UserId user = newUser(company);
        Actor actor = new Actor(user, "Persona Pérez", Role.COMPANY_ADMIN, Set.of(company));
        log.record(AuditEntry.by(actor, AuditAction.AUDIT_SETTINGS_UPDATED).onCompany(company)
                .on("COMPANY", company.value().toString(), "Empresa").with("before", 1));

        Map<String, Object> row = owner.sql("SELECT * FROM audit_event").query().singleRow();
        assertThat(row).containsEntry("actor_kind", "PERSON").containsEntry("actor_user_id", user.value())
                .containsEntry("actor_role", "COMPANY_ADMIN").containsEntry("channel", "PANEL")
                .containsEntry("action", "AUDIT_SETTINGS_UPDATED").containsEntry("outcome", "DONE")
                .containsEntry("company_id", company.value()).containsEntry("target_kind", "COMPANY")
                .containsEntry("target_label", "Empresa").containsEntry("request_id", "req-1")
                .containsEntry("prev_hash", ZEROS);
        assertThat(row.get("details").toString()).contains("\"before\": 1");
        assertThat(row.get("hash").toString()).hasSize(64);
        assertThat(owner.sql("SELECT display_name FROM audit_actor WHERE user_id = :u").param("u", user.value())
                .query(String.class).single()).isEqualTo("Persona Pérez");
        assertThat(row.values().toString()).doesNotContain("@");
    }

    @Test
    void switchedOffWritesNothing() {
        owner.sql("UPDATE audit_setting SET enabled = false").update();
        log.record(entry());
        log.recordDenied(entry());
        assertThat(rows()).isZero();
    }

    @Test
    void applicationRoleCannotChangeTheLog() {
        log.record(entry());
        for (String sql : List.of("UPDATE audit_event SET action = 'X'", "DELETE FROM audit_event",
                "TRUNCATE audit_event", "INSERT INTO audit_event (occurred_at, actor_kind, channel, action, outcome, "
                        + "chain_key, prev_hash, hash) VALUES (now(), 'SYSTEM', 'SYSTEM', 'X', 'DONE', 'main', 'a', 'b')",
                "UPDATE audit_setting SET enabled = false")) {
            assertThatThrownBy(() -> app.sql(sql).update()).as(sql).rootCause().hasMessageContaining("permission denied");
        }
        assertThat(rows()).isOne();
    }

    @Test
    void triggerStopsEvenTheOwner() {
        log.record(entry());
        assertThatThrownBy(() -> owner.sql("DELETE FROM audit_event").update()).rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> owner.sql("TRUNCATE audit_event").update()).rootCause().hasMessageContaining("append-only");
    }

    @Test
    void healthyChainVerifiesAndTamperingIsFound() {
        log.record(entry());
        log.record(entry());
        log.record(entry());
        assertThat(verify()).isNull();
        Long second = owner.sql("SELECT id FROM audit_event ORDER BY id OFFSET 1 LIMIT 1").query(Long.class).single();
        tamper("UPDATE audit_event SET target_label = 'otro' WHERE id = " + second);
        assertThat(verify()).isEqualTo(second);
    }

    @Test
    void ownerRewritingTheWholeChainPassesVerifyButNotTheSeal() {
        log.record(entry());
        log.record(entry());
        Map<String, Object> head = owner.sql("SELECT id, hash FROM audit_chain_head()").query().singleRow();
        long id = (Long) head.get("id");
        String hash = (String) head.get("hash");
        assertThat(sealMatches(id, hash)).isTrue();

        tamper("UPDATE audit_event SET target_label = 'reescrito' WHERE id = (SELECT min(id) FROM audit_event)",
                "DO $$ DECLARE r audit_event; p text := repeat('0', 64); BEGIN FOR r IN SELECT * FROM audit_event "
                        + "ORDER BY id LOOP r.prev_hash := p; r.hash := audit_event_hash(r); "
                        + "UPDATE audit_event SET prev_hash = r.prev_hash, hash = r.hash WHERE id = r.id; "
                        + "p := r.hash; END LOOP; END $$");

        assertThat(verify()).isNull();
        assertThat(sealMatches(id, hash)).isFalse();
    }

    @Test
    void twentyParallelWritesGiveOneUnbrokenChain() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<?>> done = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            done.add(pool.submit(() -> log.record(entry())));
        }
        for (Future<?> f : done) {
            f.get();
        }
        pool.shutdown();
        assertThat(rows()).isEqualTo(20);
        assertThat(owner.sql("SELECT count(DISTINCT prev_hash) FROM audit_event").query(Long.class).single())
                .isEqualTo(20);
        assertThat(verify()).isNull();
    }

    @Test
    void deniedEntryStaysWhenTheBusinessTransactionRollsBack() {
        TransactionTemplate business = new TransactionTemplate(new DataSourceTransactionManager(Db.app()));
        assertThatThrownBy(() -> business.executeWithoutResult(s -> {
            log.recordDenied(entry().denied());
            log.record(entry());
            throw new IllegalStateException("se deshace");
        })).hasMessage("se deshace");
        assertThat(owner.sql("SELECT outcome FROM audit_event").query(String.class).list()).containsExactly("DENIED");
    }

    @Test
    void businessEntryRollsBackWithItsTransaction() {
        TransactionTemplate business = new TransactionTemplate(new DataSourceTransactionManager(Db.app()));
        assertThatThrownBy(() -> business.executeWithoutResult(s -> {
            log.record(entry());
            throw new IllegalStateException("se deshace");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(rows()).isZero();
    }

    @Test
    void companyScopeSeesOnlyItsRowsAndNeverThoseOfTheGroup() {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        log.record(entry().onCompany(a));
        log.record(entry().onCompany(b));
        log.record(entry());

        assertThat(visible(CompanyScope.of(a))).isEqualTo(1);
        assertThat(visible(CompanyScope.of(b))).isEqualTo(1);
        assertThat(visible(CompanyScope.all())).isEqualTo(3);
        assertThat(visible(new CompanyScope(Set.of(), false))).isZero();
    }

    private long visible(CompanyScope scope) {
        return tx.inScope(scope, jdbc -> jdbc.sql("SELECT count(*) FROM audit_event").query(Long.class).single());
    }

    private boolean sealMatches(long id, String hash) {
        return owner.sql("SELECT audit_seal_matches(:id, :hash)").param("id", id).param("hash", hash)
                .query(Boolean.class).single();
    }

    private void tamper(String... statements) {
        owner.sql("ALTER TABLE audit_event DISABLE TRIGGER USER").update();
        try {
            for (String s : statements) {
                owner.sql(s).update();
            }
        } finally {
            owner.sql("ALTER TABLE audit_event ENABLE TRIGGER USER").update();
        }
    }
}
