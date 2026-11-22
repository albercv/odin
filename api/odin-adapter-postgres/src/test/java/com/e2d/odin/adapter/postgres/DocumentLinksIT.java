package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.citation.LinkDocuments;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class DocumentLinksIT extends AbstractPostgresIT {

    private static final String OLD_TEXT = "PÓLIZA\nPóliza n.º XYZ-19-11111\nCondiciones del seguro.";
    private static final String NEW_TEXT = "PÓLIZA\nPóliza n.º XYZ-22-22222\n\nEsta póliza sustituye y anula a la póliza n.º XYZ-19-11111 desde octubre.";

    private final PostgresDocumentLinkRepository links = new PostgresDocumentLinkRepository(tx);
    private final LinkDocuments linker = new LinkDocuments(links, Clock.fixed(NOW, ZoneOffset.UTC), 50);
    private final JdbcClient owner = JdbcClient.create(Db.owner());
    private CompanyId company;
    private UserId user;

    @BeforeEach
    void seed() {
        company = newCompany();
        user = newUser(company);
    }

    private Document doc(CompanyId of, Optional<ProjectId> project, String title, String text) {
        Document d = new Document(new DocumentId(UUID.randomUUID()), of, project, title, "f.txt",
                DocumentFormat.PLAIN_TEXT, UUID.randomUUID().toString(), DocumentStatus.READY, Optional.empty(), true,
                user, NOW, Optional.empty(), 0, Optional.empty(), Optional.empty());
        docs.save(d);
        docs.replacePages(d.id(), List.of(new DocumentPage(d.id(), 1, text, "t", "1")));
        return d;
    }

    private Document doc(String title, String text) {
        return doc(company, Optional.empty(), title, text);
    }

    private void process() {
        while (linker.processBatch() > 0) {
            // hasta que no quede nada por analizar
        }
    }

    /** Título del origen -> título del destino, o «?» si la cita sigue pendiente. */
    private Map<String, String> resolved() {
        return owner.sql("""
                SELECT s.title AS source, coalesce(t.title, '?') AS target FROM document_link l
                JOIN document s ON s.id = l.source_document_id LEFT JOIN document t ON t.id = l.target_document_id""")
                .query((rs, n) -> Map.entry(rs.getString("source"), rs.getString("target"))).list().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Test
    void exactIdentifierResolvesTheCitation() {
        doc("vieja", OLD_TEXT);
        doc("nueva", NEW_TEXT);

        process();

        assertThat(resolved()).containsExactly(Map.entry("nueva", "vieja"));
        assertThat(owner.sql("SELECT relation || '|' || origin || '|' || evidence_page FROM document_link")
                .query(String.class).single()).isEqualTo("REPLACES|DETECTED|1");
    }

    @Test
    void theCitationMayArriveBeforeOrAfterItsTarget() {
        doc("nueva", NEW_TEXT);
        process();
        assertThat(resolved()).containsExactly(Map.entry("nueva", "?"));

        doc("vieja", OLD_TEXT);
        process();
        assertThat(resolved()).containsExactly(Map.entry("nueva", "vieja"));
    }

    @Test
    void twoDocumentsWithTheSameIdentifierLeaveTheCitationPending() {
        doc("vieja", OLD_TEXT);
        doc("copia", OLD_TEXT);
        doc("nueva", NEW_TEXT);

        process();

        assertThat(resolved()).containsExactly(Map.entry("nueva", "?"));
    }

    @Test
    void aWithdrawnDocumentNoLongerCountsAndTheCitationResolvesToTheOther() {
        Document first = doc("vieja", OLD_TEXT);
        doc("copia", OLD_TEXT);
        doc("nueva", NEW_TEXT);
        process();
        owner.sql("UPDATE document SET withdrawn_at = now(), withdrawn_by = :u WHERE id = :id")
                .param("u", user.value()).param("id", first.id().value()).update();

        doc("otra", "Sin identificador.");
        process();

        assertThat(resolved()).containsExactly(Map.entry("nueva", "copia"));
    }

    @Test
    void anotherCompanyNeverAndAnotherProjectNotButGeneralYes() {
        CompanyId other = newCompany();
        ProjectId projectA = newProject(company, "A");
        ProjectId projectB = newProject(company, "B");
        doc(other, Optional.empty(), "ajena", OLD_TEXT);
        doc(company, Optional.of(projectB), "de-B", OLD_TEXT.replace("11111", "33333"));
        doc(company, Optional.empty(), "general", OLD_TEXT.replace("11111", "44444"));
        doc(company, Optional.of(projectA), "nueva",
                NEW_TEXT + " También rectifica la factura AB-2025-0302. Anula la póliza XYZ-19-33333. Anula la póliza XYZ-19-44444.");

        process();

        assertThat(owner.sql("""
                SELECT l.cited_identifier || '>' || coalesce(t.title, '?') FROM document_link l
                LEFT JOIN document t ON t.id = l.target_document_id ORDER BY 1""").query(String.class).list())
                .containsExactly("AB-2025-0302>?", "XYZ-19-11111>?", "XYZ-19-33333>?", "XYZ-19-44444>general");
    }

    @Test
    void aCompanyScopeDoesNotSeeTheLinksOfAnother() {
        doc("vieja", OLD_TEXT);
        doc("nueva", NEW_TEXT);
        process();
        CompanyId other = newCompany();

        assertThat(countAs(other, "document_link")).isZero();
        assertThat(countAs(other, "document_identifier")).isZero();
        assertThat(countAs(other, "document_link_scan")).isZero();
        assertThat(countAs(company, "document_link")).isEqualTo(1);
        assertThat(countAs(company, "document_identifier")).isEqualTo(2);
        assertThat(countAs(company, "document_link_scan")).isEqualTo(2);
    }

    private int countAs(CompanyId scope, String table) {
        return tx.inScope(CompanyScope.of(scope), ProjectVisibility.all(),
                jdbc -> jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single());
    }

    @Test
    void anotherExtractorVersionAnalyzesAgainWithoutDuplicatesAndKeepsAdminLinks() {
        Document old = doc("vieja", OLD_TEXT);
        Document next = doc("nueva", NEW_TEXT);
        process();
        owner.sql("""
                INSERT INTO document_link (id, company_id, source_document_id, target_document_id, relation,
                    cited_identifier, evidence_page, evidence_quote, origin, created_at)
                VALUES (gen_random_uuid(), :c, :s, :t, 'MODIFIES', 'MANUAL-1', 1, 'a mano', 'ADMIN', now())""")
                .param("c", company.value()).param("s", next.id().value()).param("t", old.id().value()).update();
        owner.sql("UPDATE document_link_scan SET extractor_version = 'links-0'").update();

        assertThat(linker.processBatch()).isEqualTo(2);

        assertThat(owner.sql("SELECT count(*) FROM document_link WHERE origin = 'DETECTED'").query(Integer.class)
                .single()).isEqualTo(1);
        assertThat(owner.sql("SELECT count(*) FROM document_link WHERE origin = 'ADMIN'").query(Integer.class)
                .single()).isEqualTo(1);
        assertThat(linker.processBatch()).isZero();
    }

    @Test
    void withdrawnPendingOrNotReadyDocumentsAreNotAnalyzed() {
        Document pending = newDocument(company, user, "sin leer", DocumentStatus.PROCESSING);
        docs.replacePages(pending.id(), List.of(new DocumentPage(pending.id(), 1, NEW_TEXT, "t", "1")));
        newIntakeDocument(newIntakeBatch(company, user), user, "sin colocar", DocumentStatus.READY);

        assertThat(linker.processBatch()).isZero();
    }

    @Test
    void migrationOverExistingRowsLeavesThemToBeAnalyzedAfterwards() {
        DataSource database = Db.freshDatabase("migration_document_links");
        Map<String, String> placeholders = Map.of("appPassword", APP_PASSWORD_FOR_MIGRATION);
        Flyway.configure().dataSource(database).placeholders(placeholders).target("30").load().migrate();
        JdbcClient jdbc = JdbcClient.create(database);
        UUID c = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        jdbc.sql("INSERT INTO company (id, name, code) VALUES (:id, 'Empresa', 'EMP1')").param("id", c).update();
        jdbc.sql("INSERT INTO app_user (id, first_name, person_code, active) VALUES (:id, 'Ana', 'P1', true)")
                .param("id", u).update();
        UUID documentId = insertRawDocument(jdbc, c, u);

        Flyway.configure().dataSource(database).placeholders(placeholders).load().migrate();

        assertThat(jdbc.sql("SELECT count(*) FROM document_link_scan").query(Integer.class).single()).isZero();
        LinkDocuments onInstalled = new LinkDocuments(new PostgresDocumentLinkRepository(new ScopedTransactions(database)),
                Clock.fixed(NOW, ZoneOffset.UTC), 50);
        assertThat(onInstalled.processBatch()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT identifier FROM document_identifier WHERE document_id = :d").param("d", documentId)
                .query(String.class).single()).isEqualTo("XYZ-19-11111");
    }

    private static final String APP_PASSWORD_FOR_MIGRATION = Db.APP_PASSWORD;

    private static UUID insertRawDocument(JdbcClient jdbc, UUID company, UUID user) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO document (id, company_id, title, filename, format, sha256, status, paginated, uploaded_by,
                    uploaded_at, attempt_count)
                VALUES (:id, :c, 'Antigua', 'f.pdf', 'PDF', 'abc', 'READY', true, :u, now(), 0)""")
                .param("id", id).param("c", company).param("u", user).update();
        jdbc.sql("INSERT INTO document_page (document_id, company_id, number, text, reader_name, reader_version) "
                + "VALUES (:d, :c, 1, :t, 'r', '1')").param("d", id).param("c", company).param("t", OLD_TEXT).update();
        return id;
    }
}
