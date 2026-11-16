package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.application.port.IntakeDocument;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeCounts;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.topic.TopicId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

public class PostgresDocumentRepository implements DocumentRepository {

    static final String COLUMNS = """
            d.id, d.company_id, d.project_id, d.title, d.filename, d.format, d.sha256, d.status, d.failure_reason,
            d.paginated, d.uploaded_by, d.uploaded_at, d.processing_started_at, d.attempt_count, d.superseded_by,
            d.superseded_at, d.pages_without_text, d.case_document, d.withdrawn_at, d.withdrawn_by,
            d.intake_batch_id, d.intake_pending, d.intake_path, d.scan_of""";
    private final ScopedTransactions tx;
    /** Los carriles del lector y del etiquetado terminan también lo que se retiró a mitad de trabajo (sin WITH CHECK, la RLS lo impediría). */
    private final ScopedTransactions pipeline;

    public PostgresDocumentRepository(ScopedTransactions tx) {
        this.tx = tx;
        this.pipeline = tx.includeWithdrawn(true).includeIntake(true);
    }

    @Override
    public void save(Document d) {
        pipeline.runInScope(CompanyScope.of(d.companyId()), ProjectVisibility.all(), jdbc -> upsert(jdbc, d));
    }

    @Override
    public void replacePages(DocumentId id, List<DocumentPage> pages) {
        pipeline.runInScope(CompanyScope.all(), ProjectVisibility.all(), jdbc -> {
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
        return findEntry(tx, scope, visibility, id);
    }

    @Override
    public Optional<DocumentEntry> findEntryIncludingWithdrawn(CompanyScope scope, ProjectVisibility visibility,
            DocumentId id) {
        return findEntry(pipeline, scope, visibility, id);
    }

    private static Optional<DocumentEntry> findEntry(ScopedTransactions transactions, CompanyScope scope,
            ProjectVisibility visibility, DocumentId id) {
        String sql = "SELECT " + COLUMNS + ", " + SupersessionSql.COLUMNS + " FROM document d "
                + SupersessionSql.joins("d.superseded_by", "d.company_id") + " WHERE d.id = :id AND " + Sql.inScope("d.company_id")
                + " AND " + Sql.inVisibleProjects("d.project_id") + " AND " + Sql.visibleDocument("d");
        return transactions.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility)
                .param("id", id.value()).query((rs, n) -> entry(rs)).optional());
    }

    @Override
    public void markWithdrawal(Document d) {
        try {
            pipeline.runInScope(CompanyScope.of(d.companyId()), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                    UPDATE document SET withdrawn_at = :at, withdrawn_by = :by
                    WHERE id = :id AND company_id = :company""")
                    .param("at", Sql.ts(d.withdrawnAt())).param("by", d.withdrawnBy().map(UserId::value).orElse(null))
                    .param("id", d.id().value()).param("company", d.companyId().value()).update());
        } catch (ConflictException e) {
            throw new ConflictException("Ya hay un documento igual en ese proyecto; retíralo antes de recuperar este");
        }
    }

    @Override
    public List<DocumentEntry> search(CompanyScope scope, ProjectVisibility visibility, boolean withdrawn,
            Optional<String> titleQuery, int offset, int limit) {
        String sql = "SELECT " + COLUMNS + ", " + SupersessionSql.COLUMNS + " FROM document d "
                + SupersessionSql.joins("d.superseded_by", "d.company_id") + " WHERE " + searchFilter()
                + " ORDER BY d.title, d.id OFFSET :offset LIMIT :limit";
        return searchTransactions(withdrawn).inScope(scope, visibility,
                jdbc -> bindSearch(Sql.scoped(jdbc.sql(sql), scope), visibility, withdrawn, titleQuery)
                        .param("offset", offset).param("limit", limit).query((rs, n) -> entry(rs)).list());
    }

    @Override
    public int count(CompanyScope scope, ProjectVisibility visibility, boolean withdrawn,
            Optional<String> titleQuery) {
        String sql = "SELECT count(*) FROM document d WHERE " + searchFilter();
        return searchTransactions(withdrawn).inScope(scope, visibility,
                jdbc -> bindSearch(Sql.scoped(jdbc.sql(sql), scope), visibility, withdrawn, titleQuery)
                        .query(Integer.class).single());
    }

    /**
     * Los retirados solo se ven en la sesión que activa {@code app.include_withdrawn}. Ninguna activa
     * {@code app.include_intake}: lo pendiente no sale en la lista, el buscador ni el total (D1).
     */
    private ScopedTransactions searchTransactions(boolean withdrawn) {
        return withdrawn ? tx.includeWithdrawn(true) : tx;
    }

    private static String searchFilter() {
        return "(NOT :withdrawnOnly OR d.withdrawn_at IS NOT NULL) AND (CAST(:title AS text) IS NULL "
                + "OR unaccent(lower(d.title)) LIKE unaccent(lower(CAST(:title AS text)))) AND "
                + Sql.inScope("d.company_id") + " AND " + Sql.inVisibleProjects("d.project_id") + " AND "
                + Sql.visibleDocument("d"); // con los escaneos enlazados: solo se ocultan al trabajador (D3)
    }

    private static StatementSpec bindSearch(StatementSpec spec, ProjectVisibility visibility, boolean withdrawn,
            Optional<String> titleQuery) {
        String like = titleQuery.map(q -> "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                + "%").orElse(null);
        return Sql.visible(spec, visibility).param("withdrawnOnly", withdrawn).param("title", like);
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
    public int markCaseDocuments(CompanyId companyId, Set<DocumentId> documentIds, boolean caseDocument) {
        String ids = documentIds.stream().map(id -> id.value().toString()).collect(Collectors.joining(","));
        return tx.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> {
            int updated = jdbc.sql("""
                    UPDATE document SET case_document = :flag
                    WHERE company_id = :company AND id = ANY (CAST(string_to_array(:ids, ',') AS uuid[]))""")
                    .param("flag", caseDocument).param("company", companyId.value()).param("ids", ids).update();
            if (updated != documentIds.size()) {
                // al lanzar se deshace la transacción: no cambia ninguno
                throw new NotFoundException("Documento no encontrado");
            }
            return updated;
        });
    }

    @Override
    public Optional<Document> findBySha256(CompanyId companyId, String sha256) {
        return tx.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc
                .sql("SELECT " + COLUMNS + " FROM document d WHERE d.company_id = :company AND d.sha256 = :sha "
                        + "AND d.withdrawn_at IS NULL ORDER BY d.seq LIMIT 1")
                .param("company", companyId.value()).param("sha", sha256).query((rs, n) -> document(rs)).optional());
    }

    @Override
    public Optional<Document> findBySha256ForIntake(CompanyId companyId, IntakeBatchId batchId, String sha256) {
        return pipeline.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc
                .sql("SELECT " + COLUMNS + " FROM document d WHERE d.company_id = :company AND d.sha256 = :sha "
                        + "AND (d.withdrawn_at IS NULL OR d.intake_batch_id = :batch) "
                        + "ORDER BY d.withdrawn_at IS NOT NULL, d.seq LIMIT 1")
                .param("company", companyId.value()).param("sha", sha256).param("batch", batchId.value())
                .query((rs, n) -> document(rs)).optional());
    }

    @Override
    public Set<String> existingSha256ForIntake(CompanyId companyId, IntakeBatchId batchId,
            Collection<String> sha256s) {
        return pipeline.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> Set.copyOf(jdbc.sql("""
                SELECT DISTINCT d.sha256 FROM document d WHERE d.company_id = :company
                AND d.sha256 = ANY (string_to_array(:shas, ',')) AND (d.withdrawn_at IS NULL OR d.intake_batch_id = :batch)""")
                .param("company", companyId.value()).param("batch", batchId.value())
                .param("shas", String.join(",", sha256s)).query(String.class).list()));
    }

    @Override
    public IntakeCounts intakeCounts(CompanyId companyId, IntakeBatchId batchId) {
        return pipeline.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                SELECT count(*) FILTER (WHERE withdrawn_at IS NULL) AS uploaded,
                       count(*) FILTER (WHERE withdrawn_at IS NULL
                           AND status IN ('RECEIVED', 'PROCESSING', 'PENDING_READER', 'READER_PROCESSING')) AS reading,
                       count(*) FILTER (WHERE withdrawn_at IS NULL AND intake_pending) AS pending,
                       count(*) FILTER (WHERE withdrawn_at IS NULL AND status = 'PENDING_READER') AS awaiting_reader
                FROM document WHERE company_id = :company AND intake_batch_id = :batch""")
                .param("company", companyId.value()).param("batch", batchId.value())
                .query((rs, n) -> new IntakeCounts(rs.getInt("uploaded"), rs.getInt("reading"),
                        rs.getInt("pending"), rs.getInt("awaiting_reader"))).single());
    }

    @Override
    public List<String> intakeTexts(CompanyId companyId, IntakeBatchId batchId) {
        return pipeline.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                SELECT string_agg(p.text, E'\\n' ORDER BY p.number) FROM document d
                JOIN document_page p ON p.document_id = d.id
                WHERE d.company_id = :company AND d.intake_batch_id = :batch AND d.withdrawn_at IS NULL
                AND d.status = 'READY' GROUP BY d.id ORDER BY d.seq""")
                .param("company", companyId.value()).param("batch", batchId.value()).query(String.class).list());
    }

    @Override
    public List<IntakeDocument> intakeReadyDocuments(CompanyId companyId, IntakeBatchId batchId) {
        return pipeline.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                SELECT d.id, d.intake_path, string_agg(p.text, E'\\n' ORDER BY p.number) AS text,
                       bool_or(p.reader_name = 'unlimited-ocr-2') AS scan
                FROM document d JOIN document_page p ON p.document_id = d.id
                WHERE d.company_id = :company AND d.intake_batch_id = :batch AND d.withdrawn_at IS NULL
                AND d.status = 'READY' AND d.intake_pending GROUP BY d.id ORDER BY d.seq""")
                .param("company", companyId.value()).param("batch", batchId.value())
                .query((rs, n) -> new IntakeDocument(new DocumentId(Sql.uuid(rs, "id")),
                        Optional.ofNullable(rs.getString("intake_path")), rs.getString("text"), rs.getBoolean("scan")))
                .list());
    }

    @Override
    public List<Document> intakePendingDocuments(CompanyId companyId, IntakeBatchId batchId) {
        return pipeline.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("SELECT "
                + COLUMNS + """
                 FROM document d WHERE d.company_id = :company AND d.intake_batch_id = :batch
                AND d.intake_pending AND d.withdrawn_at IS NULL ORDER BY d.seq""")
                .param("company", companyId.value()).param("batch", batchId.value())
                .query((rs, n) -> document(rs)).list());
    }

    @Override
    public java.util.Map<DocumentId, String> intakeTitles(CompanyId companyId, IntakeBatchId batchId) {
        return pipeline.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                SELECT id, title FROM document WHERE company_id = :company AND intake_batch_id = :batch""")
                .param("company", companyId.value()).param("batch", batchId.value())
                .query((rs, n) -> Map.entry(new DocumentId(Sql.uuid(rs, "id")), rs.getString("title"))).list()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @Override
    public java.util.Map<DocumentId, String> titlesOf(CompanyId companyId, java.util.Collection<DocumentId> ids) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        return pipeline.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                SELECT id, title FROM document WHERE company_id = :company AND id = ANY (string_to_array(:ids, ',')::uuid[])""")
                .param("company", companyId.value()).param("ids", ids.stream().map(d -> d.value().toString())
                        .collect(Collectors.joining(",")))
                .query((rs, n) -> Map.entry(new DocumentId(Sql.uuid(rs, "id")), rs.getString("title"))).list()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @Override
    public void confirmIntake(CompanyId companyId, DocumentId id, Optional<ProjectId> projectId, UserId by,
            Instant at) {
        int updated;
        try {
            updated = pipeline.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                    UPDATE document SET project_id = :project, intake_pending = false, intake_placed_by = :by,
                        intake_placed_at = :at
                    WHERE id = :id AND company_id = :company AND intake_pending AND withdrawn_at IS NULL""")
                    .param("project", projectId.map(ProjectId::value).orElse(null)).param("id", id.value())
                    .param("company", companyId.value()).param("by", by.value()).param("at", Sql.ts(at)).update());
        } catch (ConflictException e) {
            // el índice único de huella de lo visible (V26): alguien subió el mismo fichero a ese proyecto
            throw new ConflictException("Ya hay un documento igual en ese proyecto: descarta este");
        }
        if (updated == 0) {
            throw new ConflictException("El documento ya no está pendiente");
        }
    }

    @Override
    public void markScanOf(CompanyId companyId, DocumentId scanId, DocumentId originalId) {
        int updated = tx.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                UPDATE document SET scan_of = :original WHERE id = :id AND company_id = :company""")
                .param("original", originalId.value()).param("id", scanId.value())
                .param("company", companyId.value()).update());
        if (updated == 0) {
            throw new NotFoundException("Documento no encontrado");
        }
    }

    @Override
    public void clearScanOf(CompanyId companyId, DocumentId scanId) {
        int updated = tx.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                UPDATE document SET scan_of = NULL WHERE id = :id AND company_id = :company""")
                .param("id", scanId.value()).param("company", companyId.value()).update());
        if (updated == 0) {
            throw new NotFoundException("Documento no encontrado");
        }
    }

    @Override
    public Map<DocumentId, List<DocumentRef>> scanCopiesOf(CompanyScope scope, ProjectVisibility visibility,
            Optional<DocumentStatus> status, java.util.Collection<DocumentId> originals) {
        if (originals.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT d.scan_of, d.id, d.title FROM document d WHERE d.scan_of = ANY (CAST(string_to_array(:ids, ',') AS uuid[]))"
                + " AND (CAST(:status AS text) IS NULL OR d.status = :status) AND " + Sql.inScope("d.company_id")
                + " AND " + Sql.inVisibleProjects("d.project_id") + " AND " + Sql.visibleDocument("d")
                + " ORDER BY d.title, d.id";
        return tx.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility)
                .param("ids", idList(originals)).param("status", status.map(DocumentStatus::name).orElse(null))
                .query((rs, n) -> Map.entry(new DocumentId(Sql.uuid(rs, "scan_of")),
                        new DocumentRef(new DocumentId(Sql.uuid(rs, "id")), rs.getString("title")))).list()
                .stream().collect(Collectors.groupingBy(Map.Entry::getKey, java.util.LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList()))));
    }

    @Override
    public Map<DocumentId, DocumentRef> refsOf(CompanyScope scope, ProjectVisibility visibility,
            java.util.Collection<DocumentId> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT d.id, d.title FROM document d WHERE d.id = ANY (CAST(string_to_array(:ids, ',') AS uuid[])) AND "
                + Sql.inScope("d.company_id") + " AND " + Sql.inVisibleProjects("d.project_id") + " AND "
                + Sql.visibleDocument("d");
        return tx.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility)
                .param("ids", idList(ids))
                .query((rs, n) -> new DocumentRef(new DocumentId(Sql.uuid(rs, "id")), rs.getString("title"))).list()
                .stream().collect(Collectors.toMap(DocumentRef::id, r -> r)));
    }

    private static String idList(java.util.Collection<DocumentId> ids) {
        return ids.stream().map(d -> d.value().toString()).collect(Collectors.joining(","));
    }

    @Override
    public boolean readerQueueBusy(CompanyId companyId) {
        return pipeline.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM document WHERE company_id = :company AND withdrawn_at IS NULL
                AND status IN ('PENDING_READER', 'READER_PROCESSING'))""")
                .param("company", companyId.value()).query(Boolean.class).single());
    }

    @Override
    public Optional<Document> findBySha256InProject(CompanyId companyId, Optional<ProjectId> projectId,
            String sha256) {
        return tx.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("SELECT " + COLUMNS
                + " FROM document d WHERE d.company_id = :company AND d.sha256 = :sha "
                + "AND d.withdrawn_at IS NULL AND (d.project_id IS NULL OR d.project_id = :project) ORDER BY d.seq LIMIT 1")
                .param("company", companyId.value()).param("sha", sha256)
                .param("project", projectId.map(ProjectId::value).orElse(null)).query((rs, n) -> document(rs))
                .optional());
    }

    @Override
    public List<DocumentEntry> list(CompanyScope scope, ProjectVisibility visibility, Optional<ProjectId> projectId,
            Optional<TopicId> topicId, Optional<Boolean> caseDocument, Optional<DocumentStatus> status, int offset,
            int limit) {
        return list(tx, scope, visibility, projectId, topicId, caseDocument, status, offset, limit);
    }

    private static List<DocumentEntry> list(ScopedTransactions transactions, CompanyScope scope,
            ProjectVisibility visibility, Optional<ProjectId> projectId, Optional<TopicId> topicId,
            Optional<Boolean> caseDocument, Optional<DocumentStatus> status, int offset, int limit) {
        UUID project = projectId.map(ProjectId::value).orElse(null);
        String sql = "SELECT " + COLUMNS + ", " + SupersessionSql.COLUMNS + " FROM document d "
                + SupersessionSql.joins("d.superseded_by", "d.company_id") + """
                 WHERE (CAST(:project AS uuid) IS NULL OR d.project_id = :project)
                  AND (CAST(:topic AS uuid) IS NULL OR EXISTS (SELECT 1 FROM document_topic x
                      JOIN topic p ON p.id = x.topic_id AND p.deleted_at IS NULL
                      WHERE x.document_id = d.id AND x.topic_id = :topic AND x.state = 'ACTIVE'))
                  AND (CAST(:caseDocument AS boolean) IS NULL OR d.case_document = :caseDocument)
                  AND (CAST(:status AS text) IS NULL OR d.status = :status)
                  AND %s AND %s AND %s
                ORDER BY d.title, d.id OFFSET :offset LIMIT :limit""".formatted(Sql.inScope("d.company_id"),
                Sql.inVisibleProjects("d.project_id"), Sql.listable("d"));
        return transactions.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility)
                .param("project", project).param("topic", topicId.map(TopicId::value).orElse(null))
                .param("caseDocument", caseDocument.orElse(null))
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
                + " AND " + Sql.inVisibleProjects("d.project_id") + " AND "
                + Sql.visibleDocument("d")), scope), visibility)
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

    /** Toma el primer documento en espera (lo subido a mano antes que la primera carga) o con el trabajo caducado, bloqueado sin esperar a los que otro ya tiene. */
    private Optional<Document> claim(String waiting, String running, Instant now, Duration staleAfter,
            UnaryOperator<Document> start) {
        String sql = "SELECT " + COLUMNS + """
                 FROM document d WHERE d.withdrawn_at IS NULL AND (d.status = '%s' OR (d.status = '%s'
                    AND (d.processing_started_at IS NULL OR d.processing_started_at <= :threshold)))
                ORDER BY (d.intake_batch_id IS NOT NULL), d.seq LIMIT 1 FOR UPDATE SKIP LOCKED""".formatted(waiting, running);
        return pipeline.inScope(CompanyScope.all(), ProjectVisibility.all(), jdbc -> jdbc.sql(sql)
                .param("threshold", Sql.ts(now.minus(staleAfter))).query((rs, n) -> document(rs)).optional()
                .map(start).map(claimed -> {
                    upsert(jdbc, claimed);
                    return claimed;
                }));
    }

    static void upsert(JdbcClient jdbc, Document d) {
        jdbc.sql("""
                INSERT INTO document (id, company_id, project_id, title, filename, format, sha256, status,
                    failure_reason, paginated, uploaded_by, uploaded_at, processing_started_at, attempt_count,
                    pages_without_text, case_document, intake_batch_id, intake_pending, intake_path)
                VALUES (:id, :company, :project, :title, :filename, :format, :sha, :status, :reason, :paginated,
                    :uploader, :uploaded, :started, :attempts, :blankPages, :caseDocument, :intakeBatch,
                    :intakePending, :intakePath)
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
                .param("blankPages", d.pagesWithoutText().toArray(Integer[]::new))
                .param("caseDocument", d.caseDocument())
                .param("intakeBatch", d.intakeBatchId().map(IntakeBatchId::value).orElse(null))
                .param("intakePending", d.intakePending()).param("intakePath", d.intakePath().orElse(null)).update();
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
                List.of((Integer[]) rs.getArray("pages_without_text").getArray()), rs.getBoolean("case_document"),
                Sql.optionalInstant(rs, "withdrawn_at"),
                Optional.ofNullable(Sql.uuid(rs, "withdrawn_by")).map(UserId::new),
                Optional.ofNullable(Sql.uuid(rs, "intake_batch_id")).map(IntakeBatchId::new),
                rs.getBoolean("intake_pending"), Optional.ofNullable(rs.getString("intake_path")),
                Optional.ofNullable(Sql.uuid(rs, "scan_of")).map(DocumentId::new));
    }
}
