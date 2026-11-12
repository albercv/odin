package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.knowledge.QuestionKind;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.organization.Project;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicState;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class RowLevelSecurityIT extends AbstractPostgresIT {

    private static final List<String> TABLES = List.of("company", "project", "membership", "document", "document_page",
            "knowledge_card", "question_record", "project_member", "topic", "document_topic", "document_tagging");
    private static final List<String> PROJECT_TABLES = List.of("document", "document_page", "knowledge_card");

    private Document closedDocument;
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
        ProjectId project = new ProjectId(UUID.randomUUID());
        orgs.saveProject(new Project(project, company, "P", true));
        projectMembers.add(company, project, user);
        Document d = newDocument(company, user, "Doc", DocumentStatus.READY);
        docs.replacePages(d.id(), List.of(new DocumentPage(d.id(), 1, "texto", "lector", "1")));
        cards.save(new KnowledgeCard(new CardId(UUID.randomUUID()), company, Optional.empty(), "T", "B",
                Optional.empty(), user, NOW, CardStatus.PENDING, Optional.empty()));
        Topic topic = new Topic(new TopicId(UUID.randomUUID()), company, "Agua", "Fugas");
        new PostgresTopicRepository(tx).save(topic);
        new PostgresTaggingRepository(tx).decide(CompanyScope.of(company), d.id(), topic.id(), TopicState.ACTIVE, user,
                NOW);
                new PostgresQuestionLog(tx).record(new QuestionRecord(UUID.randomUUID(), company, user, "pregunta", 0, NOW, QuestionKind.SEARCH));
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

    private ProjectId seedInClosedProject(CompanyId company) {
        UserId user = newUser(company);
        ProjectId project = newProject(company, "Cerrado " + UUID.randomUUID());
        Document d = new Document(new DocumentId(UUID.randomUUID()), company,
                Optional.of(project), "Doc cerrado", "f.pdf", DocumentFormat.PDF,
                UUID.randomUUID().toString(), DocumentStatus.READY, Optional.empty(), true, user, NOW,
                Optional.empty(), 0, Optional.empty(), Optional.empty());
        docs.save(d);
        closedDocument = d;
        docs.replacePages(d.id(), List.of(new DocumentPage(d.id(), 1, "texto cerrado", "lector", "1")));
        cards.save(new KnowledgeCard(new CardId(UUID.randomUUID()), company, Optional.of(project), "T", "B",
                Optional.empty(), user, NOW, CardStatus.PENDING, Optional.empty()));
        return project;
    }

    /** Cuenta las filas de las tres tablas con proyecto, con las variables fijadas; las del proyecto son opcionales. */
    private void assertProjectRows(CompanyId company, String projectIds, String allProjects, long expected)
            throws SQLException {
        try (Connection c = Db.app().getConnection(); Statement s = c.createStatement()) {
            s.execute("SELECT set_config('app.company_ids', '" + company.value() + "', false)");
            if (projectIds != null) {
                s.execute("SELECT set_config('app.project_ids', '" + projectIds + "', false)");
            }
            if (allProjects != null) {
                s.execute("SELECT set_config('app.all_projects', '" + allProjects + "', false)");
            }
            for (String table : PROJECT_TABLES) {
                assertThat(count(c, table)).as(table).isEqualTo(expected);
            }
        }
    }

    @Test
    void restrictedProjectRowsAreHiddenWithoutItsIdInScope() throws SQLException {
        ProjectId project = seedInClosedProject(a);
        seedInClosedProject(a);
        assertProjectRows(a, UUID.randomUUID().toString(), "off", 1);
        assertProjectRows(a, project.value().toString(), "off", 2);
    }

    @Test
    void rowsWithoutProjectAreVisibleWithEmptyProjectScope() throws SQLException {
        seedInClosedProject(a);
        assertProjectRows(a, "", "off", 1);
    }

    @Test
    void allProjectsFlagShowsEverythingOfTheCompany() throws SQLException {
        seedInClosedProject(a);
        seedInClosedProject(b);
        assertProjectRows(a, "", "on", 2);
    }

    @Test
    void noProjectVariablesMeansOnlyProjectlessRows() throws SQLException {
        seedInClosedProject(a);
        assertProjectRows(a, null, null, 1);
    }

    @Test
    void projectMembersOfAnotherCompanyAreHidden() throws SQLException {
        ProjectId projectOfB = seedInClosedProject(b);
        UserId member = newUser(b);
        projectMembers.add(b, projectOfB, member);
        try (Connection c = Db.app().getConnection(); Statement s = c.createStatement()) {
            s.execute("SELECT set_config('app.company_ids', '" + a.value() + "', false)");
            assertThat(count(c, "project_member WHERE project_id = '" + projectOfB.value() + "'")).isZero();
            s.execute("SELECT set_config('app.company_ids', '" + b.value() + "', false)");
            assertThat(count(c, "project_member WHERE project_id = '" + projectOfB.value() + "'")).isEqualTo(1);
        }
    }

    @Test
    void documentsOfDeletedProjectStayReadableThroughRlsWithContentVisibility() {
        ProjectId project = seedInClosedProject(a);
        orgs.deleteProject(a, project, NOW);
        ProjectVisibility content = ProjectVisibility.of(Set.of(project));
        CompanyScope scope = CompanyScope.of(a);
        assertThat(docs.list(scope, content, Optional.empty(), Optional.empty(), 0, 10)).hasSize(2);
        assertThat(cards.listByStatus(scope, content, CardStatus.PENDING)).hasSize(2);
        assertThat(docs.findPage(scope, content, closedDocument.id(), 1)).isPresent();
        assertThat(docs.findPage(scope, ProjectVisibility.of(Set.of()), closedDocument.id(), 1)).isEmpty();
        assertThat(docs.list(scope, ProjectVisibility.of(Set.of()), Optional.empty(), Optional.empty(), 0, 10))
                .hasSize(1);
    }

    @Test
    void replacementInHiddenProjectIsNullEvenWithoutTheExplicitFilter() {
        UserId user = newUser(a);
        ProjectId hidden = newProject(a, "Oculto");
        Document old = newDocument(a, user, "Viejo", DocumentStatus.READY);
        Document free = newDocument(a, user, "Nuevo", DocumentStatus.READY);
        Document replacement = new Document(free.id(), a, Optional.of(hidden), free.title(), free.filename(),
                free.format(), free.sha256(), free.status(), Optional.empty(), true, user, NOW, Optional.empty(), 0,
                Optional.empty(), Optional.empty());
        docs.save(replacement);
        forceMark(old, replacement);

        // El filtro explícito deja pasar todo (ProjectVisibility.all()): solo la RLS esconde el proyecto.
        assertThat(resolve(old, ProjectVisibility.of(Set.of()))).containsExactly(Optional.empty(), Optional.empty());
        assertThat(resolve(old, ProjectVisibility.of(Set.of(hidden))))
                .containsExactly(Optional.of(DocumentRef.of(replacement)), Optional.of(DocumentRef.of(replacement)));
    }

    @Test
    void chainStopsAtTheHopTheWorkerCannotSeeEvenWithoutTheExplicitFilter() {
        UserId user = newUser(a);
        ProjectId hidden = newProject(a, "Oculto");
        Document x = newDocument(a, user, "A", DocumentStatus.READY);
        Document y = newDocument(a, user, "B", DocumentStatus.READY);
        Document z = newDocument(a, user, "C", DocumentStatus.READY);
        docs.save(new Document(z.id(), a, Optional.of(hidden), z.title(), z.filename(), z.format(), z.sha256(),
                z.status(), Optional.empty(), true, user, NOW, Optional.empty(), 0, Optional.empty(),
                Optional.empty()));
        forceMark(x, y);
        forceMark(y, z);

        assertThat(resolve(x, ProjectVisibility.of(Set.of())))
                .containsExactly(Optional.of(DocumentRef.of(y)), Optional.empty());
        assertThat(resolve(x, ProjectVisibility.of(Set.of(hidden)))).extracting(r -> r.map(DocumentRef::id))
                .containsExactly(Optional.of(y.id()), Optional.of(z.id()));
    }

    @Test
    void replacementOfAnotherCompanyIsNullEvenWithoutTheExplicitFilter() {
        Document x = newDocument(a, newUser(a), "A", DocumentStatus.READY);
        Document foreign = newDocument(b, newUser(b), "Ajeno", DocumentStatus.READY);
        forceMarkAcrossCompanies(x, foreign);
        // Solo la RLS (empresa A) esconde el documento de B: el filtro explícito deja pasar todas las empresas.
        String sql = "SELECT d.id, " + SupersessionSql.COLUMNS + " FROM document d "
                + SupersessionSql.joins("d.superseded_by", "d.company_id") + " WHERE d.id = :id";
        List<Optional<DocumentRef>> refs = tx.inScope(CompanyScope.of(a), ProjectVisibility.all(),
                jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), CompanyScope.all()), ProjectVisibility.all())
                        .param("id", x.id().value()).query((rs, n) -> List.of(SupersessionSql.direct(rs),
                                SupersessionSql.current(rs))).single());
        assertThat(refs).containsExactly(Optional.empty(), Optional.empty());
    }

    private List<Optional<DocumentRef>> resolve(Document old, ProjectVisibility rlsVisibility) {
        String sql = "SELECT d.id, " + SupersessionSql.COLUMNS + " FROM document d "
                + SupersessionSql.joins("d.superseded_by", "d.company_id") + " WHERE d.id = :id";
        CompanyScope scope = CompanyScope.of(a);
        return tx.inScope(scope, rlsVisibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope),
                ProjectVisibility.all()).param("id", old.id().value()).query((rs, n) -> List.of(
                        SupersessionSql.direct(rs), SupersessionSql.current(rs))).single());
    }

    @Test
    void projectVariablesDoNotLeakAcrossCallsOnAReusedConnection() throws SQLException {
        seedInClosedProject(a);
        try (SingleConnectionDataSource single = new SingleConnectionDataSource(Db.app().getConnection(), true)) {
            ScopedTransactions reused = new ScopedTransactions(single);
            long inside = reused.inScope(CompanyScope.of(a), ProjectVisibility.all(),
                    jdbc -> jdbc.sql("SELECT count(*) FROM document").query(Long.class).single());
            assertThat(inside).isEqualTo(2);
            Connection c = single.getConnection();
            try (Statement s = c.createStatement()) {
                s.execute("SELECT set_config('app.company_ids', '" + a.value() + "', false)");
            }
            assertThat(count(c, "document")).isEqualTo(1);
        }
    }

    @Test
    void topicTablesRejectWritesForAnotherCompany() throws SQLException {
        try (Connection c = Db.app().getConnection(); Statement s = c.createStatement()) {
            s.execute("SELECT set_config('app.company_ids', '" + a.value() + "', false)");
            String ids = "gen_random_uuid(), gen_random_uuid(), '" + b.value() + "'";
            for (String insert : List.of(
                    "INSERT INTO topic (id, company_id, name, definition) VALUES (gen_random_uuid(), '" + b.value()
                            + "', 'X', 'x')",
                    "INSERT INTO document_tagging (document_id, company_id, topic_list_version, status, attempt_count)"
                            + " VALUES (gen_random_uuid(), '" + b.value() + "', 1, 'PENDING', 0)",
                    "INSERT INTO document_topic (document_id, topic_id, company_id, source, state) VALUES ("
                            + ids + ", 'ADMIN', 'ACTIVE')")) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> s.execute(insert)).as(insert)
                        .hasMessageContaining("row-level security");
            }
        }
    }

    @Test
    void appRoleCannotDeleteTopicsButCanDeleteTheirLinksAndTaggingState() throws SQLException {
        try (Connection c = Db.app().getConnection(); Statement s = c.createStatement()) {
            s.execute("SELECT set_config('app.company_ids', '" + a.value() + "', false)");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> s.execute("DELETE FROM topic"))
                    .hasMessageContaining("permission denied");
            assertThat(s.executeUpdate("DELETE FROM document_topic")).isEqualTo(1);
            assertThat(s.executeUpdate("DELETE FROM document_tagging")).isEqualTo(1);
        }
    }
}
