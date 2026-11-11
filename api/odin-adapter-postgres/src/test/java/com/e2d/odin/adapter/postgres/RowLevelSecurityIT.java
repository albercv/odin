package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class RowLevelSecurityIT extends AbstractPostgresIT {

    private static final List<String> TABLES = List.of("company", "project", "membership", "document", "document_page",
            "knowledge_card", "question_record");

    private CompanyId a;
    private CompanyId b;

    @BeforeEach
    void seedTwoCompanies() {
        a = newCompany();
        b = newCompany();
        seed(a);
        seed(b);
    }

    private void seed(CompanyId company) {
        UserId user = newUser(company);
        orgs.saveProject(new Project(new ProjectId(UUID.randomUUID()), company, "P", true));
        Document d = newDocument(company, user, "Doc", DocumentStatus.READY);
        docs.replacePages(d.id(), List.of(new DocumentPage(d.id(), 1, "texto", "lector", "1")));
        cards.save(new KnowledgeCard(new CardId(UUID.randomUUID()), company, Optional.empty(), "T", "B",
                Optional.empty(), user, NOW, CardStatus.PENDING, Optional.empty()));
        new PostgresQuestionLog(tx).record(new QuestionRecord(UUID.randomUUID(), company, user, "pregunta", 0, NOW));
    }

    private long count(Connection c, String table) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT count(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void runIn(String companyIds, String groupScope, long expected) throws SQLException {
        try (Connection c = Db.app().getConnection(); Statement s = c.createStatement()) {
            if (companyIds != null) {
                s.execute("SELECT set_config('app.company_ids', '" + companyIds + "', false)");
            }
            if (groupScope != null) {
                s.execute("SELECT set_config('app.group_scope', '" + groupScope + "', false)");
            }
            for (String table : TABLES) {
                assertThat(count(c, table)).as(table).isEqualTo(expected);
            }
        }
    }

    @Test
    void noVariablesSeesNothing() throws SQLException {
        runIn(null, null, 0);
        runIn("", "off", 0);
    }

    @Test
    void companyScopeSeesOnlyOwnDocumentsPagesCardsAndQuestions() throws SQLException {
        runIn(a.value().toString(), "off", 1);
        try (Connection c = Db.app().getConnection(); Statement s = c.createStatement()) {
            s.execute("SELECT set_config('app.company_ids', '" + a.value() + "', false)");
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM document WHERE company_id = '" + b.value() + "'")) {
                rs.next();
                assertThat(rs.getLong(1)).isZero();
            }
        }
    }

    @Test
    void groupScopeSeesAll() throws SQLException {
        runIn(null, "on", 2);
    }

    @Test
    void appRoleCannotBypassRls() throws SQLException {
        try (Connection c = Db.app().getConnection(); Statement s = c.createStatement()) {
            s.execute("SET row_security = off");
            long seen;
            try {
                seen = count(c, "document");
            } catch (SQLException rejected) {
                seen = 0;
            }
            assertThat(seen).isZero();
        }
        runIn(null, null, 0);
    }

    @Test
    void appRoleIsNeitherSuperuserNorBypassRlsNorOwnerOfTables() throws SQLException {
        try (Connection c = Db.app().getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery(
                    "SELECT rolbypassrls, rolsuper FROM pg_roles WHERE rolname = 'odin_app'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("rolbypassrls")).isFalse();
                assertThat(rs.getBoolean("rolsuper")).isFalse();
            }
            try (ResultSet rs = s.executeQuery("SELECT tableowner FROM pg_tables WHERE tablename = 'document'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("tableowner")).isNotEqualTo("odin_app");
            }
        }
    }

    @Test
    void scopeVariablesDoNotLeakAcrossCallsOnAReusedConnection() throws SQLException {
        try (SingleConnectionDataSource single = new SingleConnectionDataSource(Db.app().getConnection(), true)) {
            ScopedTransactions reused = new ScopedTransactions(single);
            long inside = reused.inScope(CompanyScope.all(),
                    jdbc -> jdbc.sql("SELECT count(*) FROM document").query(Long.class).single());
            assertThat(inside).isEqualTo(2);
            Connection c = single.getConnection();
            assertThat(count(c, "document")).isZero();
            assertThat(count(c, "knowledge_card")).isZero();
        }
    }
}
