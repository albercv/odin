package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class PostgresDocumentRepository implements DocumentRepository {

    private static final String COLUMNS = """
            id, company_id, project_id, title, filename, format, sha256, status, failure_reason, paginated,
            uploaded_by, uploaded_at, processing_started_at, attempt_count""";
    private final ScopedTransactions tx;

    public PostgresDocumentRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public void save(Document d) {
        tx.runInScope(CompanyScope.of(d.companyId()), jdbc -> upsert(jdbc, d));
    }

    @Override
    public void replacePages(DocumentId id, List<DocumentPage> pages) {
        tx.runInScope(CompanyScope.all(), jdbc -> {
            jdbc.sql("DELETE FROM document_page WHERE document_id = :id").param("id", id.value()).update();
            for (DocumentPage p : pages) {
                jdbc.sql("""
                        INSERT INTO document_page (document_id, company_id, number, text, reader_name, reader_version)
                        SELECT id, company_id, :number, :text, :reader, :version FROM document WHERE id = :id""")
                        .param("id", id.value()).param("number", p.number()).param("text", p.text())
                        .param("reader", p.readerName()).param("version", p.readerVersion()).update();
            }
        });
    }

    @Override
    public Optional<Document> findById(CompanyScope scope, DocumentId id) {
        return tx.inScope(scope, jdbc -> jdbc.sql("SELECT " + COLUMNS + " FROM document WHERE id = :id")
                .param("id", id.value()).query((rs, n) -> document(rs)).optional());
    }

    @Override
    public Optional<Document> findBySha256(CompanyId companyId, String sha256) {
        return tx.inScope(CompanyScope.of(companyId), jdbc -> jdbc
                .sql("SELECT " + COLUMNS + " FROM document WHERE company_id = :company AND sha256 = :sha")
                .param("company", companyId.value()).param("sha", sha256).query((rs, n) -> document(rs)).optional());
    }

    @Override
    public List<Document> list(CompanyScope scope, Optional<ProjectId> projectId, int offset, int limit) {
        UUID project = projectId.map(ProjectId::value).orElse(null);
        return tx.inScope(scope, jdbc -> jdbc.sql("SELECT " + COLUMNS + """
                 FROM document WHERE (CAST(:project AS uuid) IS NULL OR project_id = :project)
                ORDER BY title, id OFFSET :offset LIMIT :limit""")
                .param("project", project).param("offset", offset).param("limit", limit)
                .query((rs, n) -> document(rs)).list());
    }

    @Override
    public Optional<DocumentPage> findPage(CompanyScope scope, DocumentId id, int pageNumber) {
        return tx.inScope(scope, jdbc -> jdbc.sql("""
                SELECT document_id, number, text, reader_name, reader_version FROM document_page
                WHERE document_id = :id AND number = :number""")
                .param("id", id.value()).param("number", pageNumber)
                .query((rs, n) -> new DocumentPage(new DocumentId(Sql.uuid(rs, "document_id")), rs.getInt("number"),
                        rs.getString("text"), rs.getString("reader_name"), rs.getString("reader_version")))
                .optional());
    }

    @Override
    public Optional<Document> claimNextForProcessing(Instant now, Duration staleAfter) {
        return tx.inScope(CompanyScope.all(), jdbc -> jdbc.sql("SELECT " + COLUMNS + """
                 FROM document WHERE status = 'RECEIVED' OR (status = 'PROCESSING'
                    AND (processing_started_at IS NULL OR processing_started_at <= :threshold))
                ORDER BY seq LIMIT 1 FOR UPDATE SKIP LOCKED""")
                .param("threshold", Sql.ts(now.minus(staleAfter))).query((rs, n) -> document(rs)).optional()
                .map(d -> d.startProcessing(now)).map(claimed -> {
                    upsert(jdbc, claimed);
                    return claimed;
                }));
    }

    private static void upsert(JdbcClient jdbc, Document d) {
        jdbc.sql("""
                INSERT INTO document (id, company_id, project_id, title, filename, format, sha256, status,
                    failure_reason, paginated, uploaded_by, uploaded_at, processing_started_at, attempt_count)
                VALUES (:id, :company, :project, :title, :filename, :format, :sha, :status, :reason, :paginated,
                    :uploader, :uploaded, :started, :attempts)
                ON CONFLICT (id) DO UPDATE SET project_id = :project, title = :title, filename = :filename,
                    format = :format, status = :status, failure_reason = :reason, paginated = :paginated,
                    processing_started_at = :started, attempt_count = :attempts""")
                .param("id", d.id().value()).param("company", d.companyId().value())
                .param("project", d.projectId().map(ProjectId::value).orElse(null)).param("title", d.title())
                .param("filename", d.filename()).param("format", d.format().name()).param("sha", d.sha256())
                .param("status", d.status().name()).param("reason", d.failureReason().orElse(null))
                .param("paginated", d.paginated()).param("uploader", d.uploadedBy().value())
                .param("uploaded", Sql.ts(d.uploadedAt())).param("started", Sql.ts(d.processingStartedAt()))
                .param("attempts", d.attemptCount()).update();
    }

    private static Document document(ResultSet rs) throws SQLException {
        return new Document(new DocumentId(Sql.uuid(rs, "id")), new CompanyId(Sql.uuid(rs, "company_id")),
                Optional.ofNullable(Sql.uuid(rs, "project_id")).map(ProjectId::new), rs.getString("title"),
                rs.getString("filename"), DocumentFormat.valueOf(rs.getString("format")), rs.getString("sha256"),
                DocumentStatus.valueOf(rs.getString("status")), Optional.ofNullable(rs.getString("failure_reason")),
                rs.getBoolean("paginated"), new UserId(Sql.uuid(rs, "uploaded_by")), Sql.instant(rs, "uploaded_at"),
                Sql.optionalInstant(rs, "processing_started_at"), rs.getInt("attempt_count"));
    }
}
