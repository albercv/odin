package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.topic.TopicId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.springframework.jdbc.core.simple.JdbcClient;

public class PostgresDocumentRepository implements DocumentRepository {

    static final String COLUMNS = """
            d.id, d.company_id, d.project_id, d.title, d.filename, d.format, d.sha256, d.status, d.failure_reason,
            d.paginated, d.uploaded_by, d.uploaded_at, d.processing_started_at, d.attempt_count, d.superseded_by,
            d.superseded_at, d.pages_without_text""";
    private final ScopedTransactions tx;

    public PostgresDocumentRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public void save(Document d) {
        tx.runInScope(CompanyScope.of(d.companyId()), ProjectVisibility.all(), jdbc -> upsert(jdbc, d));
    }

    @Override
    public void replacePages(DocumentId id, List<DocumentPage> pages) {
        tx.runInScope(CompanyScope.all(), ProjectVisibility.all(), jdbc -> {
            jdbc.sql("DELETE FROM document_page WHERE document_id = :id").param("id", id.value()).update();
            // el texto cambió: sus temas se vuelven a calcular (las decisiones del administrador se conservan)
            jdbc.sql("DELETE FROM document_tagging WHERE document_id = :id").param("id", id.value()).update();
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
    public Optional<Document> findById(CompanyScope scope, ProjectVisibility visibility, DocumentId id) {
        return findEntry(scope, visibility, id).map(DocumentEntry::document);
    }

    @Override
    public Optional<DocumentEntry> findEntry(CompanyScope scope, ProjectVisibility visibility, DocumentId id) {
        String sql = "SELECT " + COLUMNS + ", " + SupersessionSql.COLUMNS + " FROM document d "
                + SupersessionSql.joins("d.superseded_by", "d.company_id") + " WHERE d.id = :id AND " + Sql.inScope("d.company_id")
                + " AND " + Sql.inVisibleProjects("d.project_id");
        return tx.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility)
                .param("id", id.value()).query((rs, n) -> entry(rs)).optional());
    }

    @Override
    public void markSuperseded(CompanyId companyId, DocumentId id, DocumentId replacementId, Instant at) {
        updateSupersession(companyId, id, replacementId.value(), Sql.ts(at));
    }

    @Override
    public <T> T withDocumentsLocked(CompanyId companyId, DocumentId first, DocumentId second, Supplier<T> work) {
        UUID[] ids = Stream.of(first.value(), second.value()).sorted().toArray(UUID[]::new);
        return tx.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> {
            for (UUID id : ids) {
                jdbc.sql("SELECT id FROM document WHERE id = :id AND company_id = :company FOR UPDATE")
                        .param("id", id).param("company", companyId.value()).query().listOfRows();
            }
            return work.get();
        });
    }

    @Override
    public void clearSuperseded(CompanyId companyId, DocumentId id) {
        updateSupersession(companyId, id, null, null);
    }

    private void updateSupersession(CompanyId companyId, DocumentId id, UUID replacement, OffsetDateTime at) {
        int updated = tx.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                UPDATE document SET superseded_by = :replacement, superseded_at = :at
                WHERE id = :id AND company_id = :company AND (CAST(:replacement AS uuid) IS NULL OR EXISTS (
                    SELECT 1 FROM document r WHERE r.id = :replacement AND r.company_id = :company
                    AND (r.project_id IS NULL OR r.project_id IS NOT DISTINCT FROM document.project_id)))""")
                .param("replacement", replacement).param("at", at).param("id", id.value())
                .param("company", companyId.value()).update());
        if (updated == 0 && replacement != null) {
            throw new ConflictException("El documento que lo sustituye tiene que ser de la misma empresa y estar en "
                    + "el mismo proyecto o sin proyecto");
        }
    }

    @Override
    public Optional<Document> findBySha256(CompanyId companyId, String sha256) {
        return tx.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc
                .sql("SELECT " + COLUMNS + " FROM document d WHERE d.company_id = :company AND d.sha256 = :sha")
                .param("company", companyId.value()).param("sha", sha256).query((rs, n) -> document(rs)).optional());
    }

    @Override
    public List<DocumentEntry> list(CompanyScope scope, ProjectVisibility visibility, Optional<ProjectId> projectId,
            Optional<TopicId> topicId, Optional<DocumentStatus> status, int offset, int limit) {
        UUID project = projectId.map(ProjectId::value).orElse(null);
        String sql = "SELECT " + COLUMNS + ", " + SupersessionSql.COLUMNS + " FROM document d "
                + SupersessionSql.joins("d.superseded_by", "d.company_id") + """
                 WHERE (CAST(:project AS uuid) IS NULL OR d.project_id = :project)
                  AND (CAST(:topic AS uuid) IS NULL OR EXISTS (SELECT 1 FROM document_topic x
                      JOIN topic p ON p.id = x.topic_id AND p.deleted_at IS NULL
                      WHERE x.document_id = d.id AND x.topic_id = :topic AND x.state = 'ACTIVE'))
                  AND (CAST(:status AS text) IS NULL OR d.status = :status) AND %s AND %s
                ORDER BY d.title, d.id OFFSET :offset LIMIT :limit""".formatted(Sql.inScope("d.company_id"),
                Sql.inVisibleProjects("d.project_id"));
        return tx.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility)
                .param("project", project).param("topic", topicId.map(TopicId::value).orElse(null))
                .param("status", status.map(DocumentStatus::name).orElse(null)).param("offset", offset)
                .param("limit", limit).query((rs, n) -> entry(rs)).list());
    }

    @Override
    public Optional<DocumentPage> findPage(CompanyScope scope, ProjectVisibility visibility, DocumentId id,
            int pageNumber) {
        return tx.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql("""
                SELECT p.document_id, p.number, p.text, p.reader_name, p.reader_version
                FROM document_page p JOIN document d ON d.id = p.document_id
                WHERE p.document_id = :id AND p.number = :number AND """ + Sql.inScope("p.company_id")
                + " AND " + Sql.inVisibleProjects("d.project_id")), scope), visibility)
                .param("id", id.value()).param("number", pageNumber)
                .query((rs, n) -> new DocumentPage(new DocumentId(Sql.uuid(rs, "document_id")), rs.getInt("number"),
                        rs.getString("text"), rs.getString("reader_name"), rs.getString("reader_version")))
                .optional());
    }

    @Override
    public Optional<Document> claimNextForProcessing(Instant now, Duration staleAfter) {
        return claim("RECEIVED", "PROCESSING", now, staleAfter, d -> d.startProcessing(now));
    }

    @Override
    public Optional<Document> claimNextForReader(Instant now, Duration staleAfter) {
        return claim("PENDING_READER", "READER_PROCESSING", now, staleAfter, d -> d.startReading(now));
    }

    /** Toma el primer documento en espera o con el trabajo caducado, bloqueado sin esperar a los que otro ya tiene. */
    private Optional<Document> claim(String waiting, String running, Instant now, Duration staleAfter,
            UnaryOperator<Document> start) {
        String sql = "SELECT " + COLUMNS + """
                 FROM document d WHERE d.status = '%s' OR (d.status = '%s'
                    AND (d.processing_started_at IS NULL OR d.processing_started_at <= :threshold))
                ORDER BY d.seq LIMIT 1 FOR UPDATE SKIP LOCKED""".formatted(waiting, running);
        return tx.inScope(CompanyScope.all(), ProjectVisibility.all(), jdbc -> jdbc.sql(sql)
                .param("threshold", Sql.ts(now.minus(staleAfter))).query((rs, n) -> document(rs)).optional()
                .map(start).map(claimed -> {
                    upsert(jdbc, claimed);
                    return claimed;
                }));
    }

    private static void upsert(JdbcClient jdbc, Document d) {
        jdbc.sql("""
                INSERT INTO document (id, company_id, project_id, title, filename, format, sha256, status,
                    failure_reason, paginated, uploaded_by, uploaded_at, processing_started_at, attempt_count,
                    pages_without_text)
                VALUES (:id, :company, :project, :title, :filename, :format, :sha, :status, :reason, :paginated,
                    :uploader, :uploaded, :started, :attempts, :blankPages)
                ON CONFLICT (id) DO UPDATE SET project_id = :project, title = :title, filename = :filename,
                    format = :format, status = :status, failure_reason = :reason, paginated = :paginated,
                    processing_started_at = :started, attempt_count = :attempts,
                    pages_without_text = :blankPages""")
                .param("id", d.id().value()).param("company", d.companyId().value())
                .param("project", d.projectId().map(ProjectId::value).orElse(null)).param("title", d.title())
                .param("filename", d.filename()).param("format", d.format().name()).param("sha", d.sha256())
                .param("status", d.status().name()).param("reason", d.failureReason().orElse(null))
                .param("paginated", d.paginated()).param("uploader", d.uploadedBy().value())
                .param("uploaded", Sql.ts(d.uploadedAt())).param("started", Sql.ts(d.processingStartedAt()))
                .param("attempts", d.attemptCount())
                .param("blankPages", d.pagesWithoutText().toArray(Integer[]::new)).update();
    }

    private static DocumentEntry entry(ResultSet rs) throws SQLException {
        return new DocumentEntry(document(rs), SupersessionSql.direct(rs), SupersessionSql.current(rs));
    }

    static Document document(ResultSet rs) throws SQLException {
        return new Document(new DocumentId(Sql.uuid(rs, "id")), new CompanyId(Sql.uuid(rs, "company_id")),
                Optional.ofNullable(Sql.uuid(rs, "project_id")).map(ProjectId::new), rs.getString("title"),
                rs.getString("filename"), DocumentFormat.valueOf(rs.getString("format")), rs.getString("sha256"),
                DocumentStatus.valueOf(rs.getString("status")), Optional.ofNullable(rs.getString("failure_reason")),
                rs.getBoolean("paginated"), new UserId(Sql.uuid(rs, "uploaded_by")), Sql.instant(rs, "uploaded_at"),
                Sql.optionalInstant(rs, "processing_started_at"), rs.getInt("attempt_count"),
                Optional.ofNullable(Sql.uuid(rs, "superseded_by")).map(DocumentId::new),
                Sql.optionalInstant(rs, "superseded_at"),
                List.of((Integer[]) rs.getArray("pages_without_text").getArray()));
    }
}
