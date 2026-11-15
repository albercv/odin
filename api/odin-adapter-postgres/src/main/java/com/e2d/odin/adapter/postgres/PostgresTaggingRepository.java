package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.TaggingBacklog;
import com.e2d.odin.application.port.TaggingJob;
import com.e2d.odin.application.port.TaggingRepository;
import com.e2d.odin.application.shared.TaggingSettings;
import com.e2d.odin.application.topic.TaggingChunks;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.topic.DocumentTagging;
import com.e2d.odin.domain.topic.DocumentTopic;
import com.e2d.odin.domain.topic.TaggingStatus;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicSource;
import com.e2d.odin.domain.topic.TopicState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;

public class PostgresTaggingRepository implements TaggingRepository {

    private static final Duration DEADLINE_MARGIN = Duration.ofMinutes(5);
    private static final String LIVE_TOPIC = """
            EXISTS (SELECT 1 FROM topic p WHERE p.company_id = d.company_id AND p.deleted_at IS NULL)""";
    /** Documento leído de una empresa con temas que no está {@code DONE} con la versión actual de su lista. */
    static final String PENDING = """
            FROM document d JOIN company c ON c.id = d.company_id
            LEFT JOIN document_tagging t ON t.document_id = d.id
            WHERE d.status = 'READY' AND d.withdrawn_at IS NULL AND %s AND %s AND %%s
              AND (t.document_id IS NULL OR NOT (t.status = 'DONE' AND t.topic_list_version = c.topic_list_version))"""
            .formatted(LIVE_TOPIC, Sql.inScope("d.company_id"));
    /**
     * Sin fila, o con la versión actual: 0; con una anterior: 1. Dentro de cada grupo, lo subido a mano antes que la primera carga y luego por antigüedad. NO KEY UPDATE:
     * una decisión o un guardado a la vez (su clave ajena toma KEY SHARE sobre el documento) no se bloquean con la toma.
     */
    private static final String CLAIM = """
            SELECT d.id, c.name AS company_name, c.topic_list_version, t.topic_list_version AS tagged_version,
                t.attempt_count AS tagged_attempts
            FROM document d JOIN company c ON c.id = d.company_id
            LEFT JOIN document_tagging t ON t.document_id = d.id
            WHERE d.status = 'READY' AND d.withdrawn_at IS NULL AND %s AND %s AND %s AND (t.document_id IS NULL
                OR (t.status = 'TAGGING' AND (t.deadline_at IS NULL OR t.deadline_at < :now))
                OR (t.status <> 'TAGGING' AND (t.status = 'PENDING' OR t.topic_list_version < c.topic_list_version)))
            ORDER BY CASE WHEN t.document_id IS NULL OR t.topic_list_version = c.topic_list_version THEN 0 ELSE 1 END,
                (d.intake_batch_id IS NOT NULL), d.seq
            LIMIT 1 FOR NO KEY UPDATE OF d SKIP LOCKED""".formatted(LIVE_TOPIC, Sql.inScope("d.company_id"),
            Sql.inVisibleProjects("d.project_id"));

    private record Claimed(UUID id, String companyName, int listVersion, Integer taggedVersion, Integer taggedAttempts) {
        int attempt() {
            return taggedVersion == null || taggedVersion < listVersion ? 1 : taggedAttempts + 1;
        }
    }

    private final ScopedTransactions tx;
    /** El etiquetado ve también lo retirado a mitad de trabajo para poder terminarlo, pero nunca toma un retirado. */
    private final ScopedTransactions pipeline;

    public PostgresTaggingRepository(ScopedTransactions tx) {
        this.tx = tx;
        this.pipeline = tx.includeWithdrawn(true);
    }

    @Override
    public Optional<TaggingJob> claimNext(Instant now, TaggingSettings settings) {
        CompanyScope all = CompanyScope.all();
        ProjectVisibility everything = ProjectVisibility.all();
        return pipeline.inScope(all, everything, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(CLAIM), all), everything)
                .param("now", Sql.ts(now)).query((rs, n) -> new Claimed(Sql.uuid(rs, "id"),
                        rs.getString("company_name"), rs.getInt("topic_list_version"),
                        (Integer) rs.getObject("tagged_version"), (Integer) rs.getObject("tagged_attempts")))
                .optional().map(claimed -> claim(jdbc, claimed, now, settings)));
    }

    private TaggingJob claim(JdbcClient jdbc, Claimed claimed, Instant now, TaggingSettings settings) {
        Document document = jdbc.sql("SELECT " + PostgresDocumentRepository.COLUMNS + " FROM document d WHERE d.id = :id")
                .param("id", claimed.id()).query((rs, n) -> PostgresDocumentRepository.document(rs)).single();
        List<DocumentPage> pages = pagesOf(jdbc, claimed.id());
        List<Topic> topics = jdbc.sql("""
                SELECT id, company_id, name, definition FROM topic
                WHERE company_id = :company AND deleted_at IS NULL ORDER BY lower(name), id""")
                .param("company", document.companyId().value()).query((rs, n) -> topic(rs)).list();
        int chunks = Math.max(1, TaggingChunks.of(pages, settings.chunkCharacters()).size());
        Instant deadline = now.plus(settings.chunkTimeout().multipliedBy(chunks)).plus(DEADLINE_MARGIN);
        jdbc.sql("""
                INSERT INTO document_tagging (document_id, company_id, topic_list_version, status, attempt_count,
                    deadline_at)
                VALUES (:id, :company, :version, 'TAGGING', :attempt, :deadline)
                ON CONFLICT (document_id) DO UPDATE SET topic_list_version = :version, status = 'TAGGING',
                    attempt_count = :attempt, deadline_at = :deadline, failure_reason = NULL, tagger_version = NULL""")
                .param("id", claimed.id()).param("company", document.companyId().value())
                .param("version", claimed.listVersion()).param("attempt", claimed.attempt())
                .param("deadline", Sql.ts(deadline)).update();
        return new TaggingJob(document, pages, topics, claimed.companyName(), claimed.listVersion(), claimed.attempt());
    }

    private static List<DocumentPage> pagesOf(JdbcClient jdbc, UUID documentId) {
        return jdbc.sql("""
                SELECT document_id, number, text, reader_name, reader_version FROM document_page
                WHERE document_id = :id ORDER BY number""").param("id", documentId)
                .query((rs, n) -> new DocumentPage(new DocumentId(Sql.uuid(rs, "document_id")), rs.getInt("number"),
                        rs.getString("text"), rs.getString("reader_name"), rs.getString("reader_version")))
                .list();
    }

    @Override
    public void saveResult(DocumentId documentId, Set<TopicId> aiTopics, String taggerVersion, Instant now) {
        pipeline.runInScope(CompanyScope.all(), ProjectVisibility.all(), jdbc -> {
            // sin fila en TAGGING, el texto se volvió a leer o se pidió etiquetar de nuevo: este resultado ya no vale
            if (!"TAGGING".equals(lockRow(jdbc, CompanyScope.all(), documentId).orElse(""))) {
                return;
            }
            jdbc.sql("""
                    DELETE FROM document_topic WHERE document_id = :id AND source = 'AI' AND decided_by IS NULL""")
                    .param("id", documentId.value()).update();
            for (TopicId topic : aiTopics) {
                insertAiTopic(jdbc, documentId, topic, taggerVersion);
            }
            jdbc.sql("""
                    UPDATE document_tagging SET status = 'DONE', deadline_at = NULL, failure_reason = NULL,
                        tagger_version = :version WHERE document_id = :id""")
                    .param("version", taggerVersion).param("id", documentId.value()).update();
        });
    }

    /** Bloquea la fila del documento, si es de una empresa del ámbito, y dice su estado, si la hay. */
    private static Optional<String> lockRow(JdbcClient jdbc, CompanyScope scope, DocumentId documentId) {
        return Sql.scoped(jdbc.sql("""
                SELECT t.status FROM document_tagging t JOIN document d ON d.id = t.document_id
                WHERE t.document_id = :id AND %s FOR UPDATE OF t""".formatted(Sql.inScope("d.company_id"))), scope)
                .param("id", documentId.value()).query(String.class).optional();
    }

    /** Si el administrador ya decidió sobre el tema, la fila existe y no se toca. */
    private static void insertAiTopic(JdbcClient jdbc, DocumentId documentId, TopicId topic, String taggerVersion) {
        jdbc.sql("""
                INSERT INTO document_topic (document_id, topic_id, company_id, source, state, tagger_version)
                SELECT d.id, p.id, d.company_id, 'AI', 'ACTIVE', :version FROM document d
                JOIN topic p ON p.company_id = d.company_id AND p.id = :topic AND p.deleted_at IS NULL
                WHERE d.id = :id ON CONFLICT (document_id, topic_id) DO NOTHING""")
                .param("version", taggerVersion).param("topic", topic.value()).param("id", documentId.value())
                .update();
    }

    @Override
    public void markFailed(DocumentId documentId, String reason) {
        tx.runInScope(CompanyScope.all(), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                UPDATE document_tagging SET status = 'FAILED', failure_reason = :reason, deadline_at = NULL
                WHERE document_id = :id""").param("reason", reason).param("id", documentId.value()).update());
    }

    @Override
    public void returnToQueue(DocumentId documentId, boolean countAttempt) {
        tx.runInScope(CompanyScope.all(), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                UPDATE document_tagging SET status = 'PENDING', deadline_at = NULL,
                    attempt_count = CASE WHEN :count THEN attempt_count ELSE greatest(attempt_count - 1, 0) END
                WHERE document_id = :id""").param("count", countAttempt).param("id", documentId.value()).update());
    }

    @Override
    public void retag(CompanyScope scope, DocumentId documentId) {
        tx.runInScope(scope, ProjectVisibility.all(), jdbc -> Sql.scoped(jdbc.sql(
                "DELETE FROM document_tagging WHERE document_id = :id AND " + Sql.inScope("company_id")), scope)
                .param("id", documentId.value()).update());
    }

    @Override
    public List<DocumentTopic> topicsOf(CompanyScope scope, DocumentId documentId) {
        return topicsIn(scope, ProjectVisibility.all(), List.of(documentId), "");
    }

    @Override
    public Map<DocumentId, List<DocumentTopic>> activeTopicsOf(CompanyScope scope, ProjectVisibility visibility,
            Collection<DocumentId> documentIds) {
        return topicsIn(scope, visibility, documentIds, "AND x.state = 'ACTIVE'").stream()
                .collect(Collectors.groupingBy(DocumentTopic::documentId));
    }

    private List<DocumentTopic> topicsIn(CompanyScope scope, ProjectVisibility visibility,
            Collection<DocumentId> documentIds, String extraFilter) {
        String sql = """
                SELECT x.document_id, x.topic_id, x.source, x.state, x.tagger_version, x.decided_by, x.decided_at
                FROM document_topic x JOIN topic p ON p.id = x.topic_id AND p.deleted_at IS NULL
                JOIN document d ON d.id = x.document_id
                WHERE x.document_id = ANY (CAST(string_to_array(:ids, ',') AS uuid[])) %s AND %s AND %s
                ORDER BY lower(p.name), p.id""".formatted(extraFilter, Sql.inScope("x.company_id"),
                Sql.inVisibleProjects("d.project_id"));
        return tx.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility)
                .param("ids", idList(documentIds)).query((rs, n) -> documentTopic(rs)).list());
    }

    @Override
    public Map<DocumentId, DocumentTagging> taggingOf(CompanyScope scope, ProjectVisibility visibility,
            Collection<DocumentId> documentIds) {
        String sql = """
                SELECT t.document_id, t.company_id, t.topic_list_version, t.status, t.attempt_count, t.deadline_at,
                    t.failure_reason, t.tagger_version
                FROM document_tagging t JOIN document d ON d.id = t.document_id
                WHERE t.document_id = ANY (CAST(string_to_array(:ids, ',') AS uuid[])) AND %s AND %s"""
                .formatted(Sql.inScope("t.company_id"), Sql.inVisibleProjects("d.project_id"));
        return tx.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility)
                .param("ids", idList(documentIds)).query((rs, n) -> tagging(rs)).list().stream()
                .collect(Collectors.toMap(DocumentTagging::documentId, Function.identity())));
    }

    private static String idList(Collection<DocumentId> documentIds) {
        return documentIds.stream().map(id -> id.value().toString()).collect(Collectors.joining(","));
    }

    @Override
    public void decide(CompanyScope scope, DocumentId documentId, TopicId topicId, TopicState state, UserId by,
            Instant at) {
        tx.runInScope(scope, ProjectVisibility.all(), jdbc -> {
            ensureRow(jdbc, scope, documentId);
            if (lockRow(jdbc, scope, documentId).isPresent()) {
                upsertDecision(jdbc, scope, documentId, topicId, state, by, at);
            }
        });
    }

    /** El documento pendiente de etiquetar sigue en la cola: la fila nueva es {@code PENDING} con la versión actual. */
    private static void ensureRow(JdbcClient jdbc, CompanyScope scope, DocumentId documentId) {
        Sql.scoped(jdbc.sql("""
                INSERT INTO document_tagging (document_id, company_id, topic_list_version, status, attempt_count)
                SELECT d.id, d.company_id, c.topic_list_version, 'PENDING', 0
                FROM document d JOIN company c ON c.id = d.company_id WHERE d.id = :id AND %s
                ON CONFLICT (document_id) DO NOTHING""".formatted(Sql.inScope("d.company_id"))), scope)
                .param("id", documentId.value()).update();
    }

    private static void upsertDecision(JdbcClient jdbc, CompanyScope scope, DocumentId documentId, TopicId topicId,
            TopicState state, UserId by, Instant at) {
        Sql.scoped(jdbc.sql("""
                INSERT INTO document_topic (document_id, topic_id, company_id, source, state, decided_by, decided_at)
                SELECT d.id, p.id, d.company_id, 'ADMIN', CAST(:state AS text), :by, :at FROM document d
                JOIN topic p ON p.company_id = d.company_id AND p.id = :topic AND p.deleted_at IS NULL
                WHERE d.id = :id AND %s
                ON CONFLICT (document_id, topic_id) DO UPDATE SET state = CAST(:state AS text), decided_by = :by,
                    decided_at = :at,
                    source = CASE WHEN CAST(:state AS text) = 'REMOVED' THEN document_topic.source ELSE 'ADMIN' END,
                    tagger_version = CASE WHEN CAST(:state AS text) = 'REMOVED'
                        THEN document_topic.tagger_version ELSE NULL END""".formatted(Sql.inScope("d.company_id"))), scope)
                .param("state", state.name()).param("by", by.value()).param("at", Sql.ts(at))
                .param("topic", topicId.value()).param("id", documentId.value()).update();
    }

    @Override
    public int pendingCount(CompanyId companyId, ProjectVisibility visibility) {
        CompanyScope scope = CompanyScope.of(companyId);
        String sql = "SELECT count(*) " + PENDING.formatted(Sql.inVisibleProjects("d.project_id"));
        return tx.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility)
                .query(Integer.class).single());
    }

    @Override
    public TaggingBacklog backlog(CompanyId companyId) {
        CompanyScope scope = CompanyScope.of(companyId);
        ProjectVisibility everything = ProjectVisibility.all();
        String sql = "SELECT count(*) AS pending, min(greatest(d.uploaded_at, d.processing_started_at, "
                + "c.topic_list_changed_at)) AS since " + PENDING.formatted(Sql.inVisibleProjects("d.project_id"));
        return tx.inScope(scope, everything, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope), everything)
                .query((rs, n) -> new TaggingBacklog(rs.getInt("pending"), Sql.optionalInstant(rs, "since")))
                .single());
    }

    private static Topic topic(ResultSet rs) throws SQLException {
        return new Topic(new TopicId(Sql.uuid(rs, "id")), new CompanyId(Sql.uuid(rs, "company_id")),
                rs.getString("name"), rs.getString("definition"));
    }

    private static DocumentTagging tagging(ResultSet rs) throws SQLException {
        return new DocumentTagging(new DocumentId(Sql.uuid(rs, "document_id")),
                new CompanyId(Sql.uuid(rs, "company_id")), rs.getInt("topic_list_version"),
                TaggingStatus.valueOf(rs.getString("status")), rs.getInt("attempt_count"),
                Sql.optionalInstant(rs, "deadline_at"), Optional.ofNullable(rs.getString("failure_reason")),
                Optional.ofNullable(rs.getString("tagger_version")));
    }

    private static DocumentTopic documentTopic(ResultSet rs) throws SQLException {
        return new DocumentTopic(new DocumentId(Sql.uuid(rs, "document_id")), new TopicId(Sql.uuid(rs, "topic_id")),
                TopicSource.valueOf(rs.getString("source")), TopicState.valueOf(rs.getString("state")),
                Optional.ofNullable(rs.getString("tagger_version")),
                Optional.ofNullable(Sql.uuid(rs, "decided_by")).map(UserId::new), Sql.optionalInstant(rs, "decided_at"));
    }
}
