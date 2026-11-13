package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.domain.casefile.CaseFileAction;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.RowMapper;

public class PostgresCaseFileRepository implements CaseFileRepository {

    /** Primera barrera: empresa y proyectos visibles, y sin retirados, para los dos documentos de la relación. */
    private static final String VISIBLE = "AND " + Sql.inScope("x.company_id") + """
             AND EXISTS (SELECT 1 FROM document c WHERE c.id = x.case_document_id AND %s AND %s)
             AND EXISTS (SELECT 1 FROM document m WHERE m.id = x.document_id AND %s AND %s)""".formatted(
            Sql.inVisibleProjects("c.project_id"), Sql.notWithdrawn("c.withdrawn_at"),
            Sql.inVisibleProjects("m.project_id"), Sql.notWithdrawn("m.withdrawn_at"));

    /** Las dos puntas de la relación sin retirar, mirando directamente la columna (sin la RLS). */
    private static final String NOT_HIDDEN = """
            (NOT EXISTS (SELECT 1 FROM document c WHERE c.id = x.case_document_id AND c.withdrawn_at IS NOT NULL)
            AND NOT EXISTS (SELECT 1 FROM document m WHERE m.id = x.document_id AND m.withdrawn_at IS NOT NULL))""";

    private final ScopedTransactions tx;

    public PostgresCaseFileRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public boolean add(CaseFileEntry e) {
        return tx.inScope(CompanyScope.of(e.companyId()), ProjectVisibility.all(), jdbc -> insertEntry(jdbc, e));
    }

    @Override
    public int addAll(List<CaseFileEntry> entries, Function<CaseFileEntry, CaseFileEvent> eventFor) {
        Set<CompanyId> companies = entries.stream().map(CaseFileEntry::companyId).collect(Collectors.toSet());
        return tx.inScope(new CompanyScope(companies, false), ProjectVisibility.all(), jdbc -> {
            int added = 0;
            for (CaseFileEntry e : entries) {
                if (insertEntry(jdbc, e)) {
                    insertEvent(jdbc, eventFor.apply(e));
                    added++;
                }
            }
            return added;
        });
    }

    @Override
    public void addNewDocument(Document document, CaseFileEntry entry, CaseFileEvent event) {
        tx.runInScope(CompanyScope.of(document.companyId()), ProjectVisibility.all(), jdbc -> {
            PostgresDocumentRepository.upsert(jdbc, document);
            insertEntry(jdbc, entry);
            insertEvent(jdbc, event);
        });
    }

    @Override
    public boolean remove(CompanyId companyId, DocumentId caseId, DocumentId documentId) {
        return tx.inScope(CompanyScope.of(companyId), ProjectVisibility.all(),
                jdbc -> deleteEntry(jdbc, companyId, caseId, documentId));
    }

    @Override
    public boolean removeAndRecord(CaseFileEvent e) {
        return tx.inScope(CompanyScope.of(e.companyId()), ProjectVisibility.all(), jdbc -> {
            boolean removed = deleteEntry(jdbc, e.companyId(), e.caseId(), e.documentId());
            if (removed) {
                insertEvent(jdbc, e);
            }
            return removed;
        });
    }

    @Override
    public void record(CaseFileEvent e) {
        tx.runInScope(CompanyScope.of(e.companyId()), ProjectVisibility.all(), jdbc -> insertEvent(jdbc, e));
    }

    private static boolean insertEntry(JdbcClient jdbc, CaseFileEntry e) {
        return jdbc.sql("""
                INSERT INTO case_file_entry (company_id, case_document_id, document_id, added_by, added_at)
                VALUES (:company, :case, :document, :by, :at) ON CONFLICT DO NOTHING""")
                .param("company", e.companyId().value()).param("case", e.caseId().value())
                .param("document", e.documentId().value()).param("by", e.addedBy().value())
                .param("at", Sql.ts(e.addedAt())).update() == 1;
    }

    private static boolean deleteEntry(JdbcClient jdbc, CompanyId companyId, DocumentId caseId,
            DocumentId documentId) {
        return jdbc.sql("""
                DELETE FROM case_file_entry WHERE company_id = :company AND case_document_id = :case
                AND document_id = :document""").param("company", companyId.value()).param("case", caseId.value())
                .param("document", documentId.value()).update() == 1;
    }

    private static void insertEvent(JdbcClient jdbc, CaseFileEvent e) {
        jdbc.sql("""
                INSERT INTO case_file_event (id, company_id, case_document_id, document_id, action, actor_id, at)
                VALUES (:id, :company, :case, :document, :action, :actor, :at)""")
                .param("id", e.id()).param("company", e.companyId().value()).param("case", e.caseId().value())
                .param("document", e.documentId().value()).param("action", e.action().name())
                .param("actor", e.actorId().value()).param("at", Sql.ts(e.at())).update();
    }

    @Override
    public List<CaseFileEntry> entries(CompanyScope scope, ProjectVisibility visibility, DocumentId caseId) {
        return query(scope, visibility, caseId, """
                SELECT x.company_id, x.case_document_id, x.document_id, x.added_by, x.added_at
                FROM case_file_entry x WHERE x.case_document_id = :case %s ORDER BY x.added_at, x.document_id""",
                (rs, n) -> new CaseFileEntry(new CompanyId(Sql.uuid(rs, "company_id")),
                        new DocumentId(Sql.uuid(rs, "case_document_id")), new DocumentId(Sql.uuid(rs, "document_id")),
                        new UserId(Sql.uuid(rs, "added_by")), Sql.instant(rs, "added_at")));
    }

    @Override
    public List<CaseFileEvent> events(CompanyScope scope, ProjectVisibility visibility, DocumentId caseId) {
        return query(scope, visibility, caseId, """
                SELECT x.id, x.company_id, x.case_document_id, x.document_id, x.action, x.actor_id, x.at
                FROM case_file_event x WHERE x.case_document_id = :case %s ORDER BY x.at, x.id""",
                (rs, n) -> new CaseFileEvent(Sql.uuid(rs, "id"), new CompanyId(Sql.uuid(rs, "company_id")),
                        new DocumentId(Sql.uuid(rs, "case_document_id")), new DocumentId(Sql.uuid(rs, "document_id")),
                        CaseFileAction.valueOf(rs.getString("action")), new UserId(Sql.uuid(rs, "actor_id")),
                        Sql.instant(rs, "at")));
    }

    private <T> List<T> query(CompanyScope scope, ProjectVisibility visibility, DocumentId caseId, String sql,
            RowMapper<T> mapper) {
        return tx.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql.formatted(VISIBLE)), scope),
                visibility).param("case", caseId.value()).query(mapper).list());
    }

    /** Lo retirado a la vista: lo único que le queda al caso sin verse son las relaciones con documentos retirados. */
    private static int deleteHidden(JdbcClient jdbc, CompanyId companyId, UUID caseId, UserId actor, Instant at) {
        return jdbc.sql("""
                WITH removed AS (DELETE FROM case_file_entry x WHERE x.company_id = :company
                    AND x.case_document_id = :case AND NOT %s RETURNING x.document_id)
                INSERT INTO case_file_event (id, company_id, case_document_id, document_id, action, actor_id, at)
                SELECT gen_random_uuid(), :company, :case, document_id, 'REMOVED', :actor, :at FROM removed"""
                .formatted(NOT_HIDDEN)).param("company", companyId.value()).param("case", caseId)
                .param("actor", actor.value()).param("at", Sql.ts(at)).update();
    }

    @Override
    public int unmarkCases(CompanyId companyId, Set<DocumentId> caseIds, UserId actor, Instant at) {
        List<UUID> ids = caseIds.stream().map(DocumentId::value).toList();
        return tx.includeWithdrawn(true).inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> {
            int found = jdbc.sql("SELECT count(*) FROM (SELECT id FROM document WHERE company_id = :company "
                    + "AND id IN (:ids) ORDER BY id FOR UPDATE) locked").param("company", companyId.value())
                    .param("ids", ids).query(Integer.class).single();
            if (found != ids.size()) {
                throw new NotFoundException("Documento no encontrado");
            }
            int visible = jdbc.sql("SELECT count(*) FROM case_file_entry x WHERE x.company_id = :company "
                    + "AND x.case_document_id IN (:ids) AND " + NOT_HIDDEN).param("company", companyId.value())
                    .param("ids", ids).query(Integer.class).single();
            if (visible > 0) {
                throw new ConflictException("Quita antes los documentos del expediente");
            }
            ids.forEach(id -> deleteHidden(jdbc, companyId, id, actor, at));
            return jdbc.sql("UPDATE document SET case_document = false WHERE company_id = :company AND id IN (:ids)")
                    .param("company", companyId.value()).param("ids", ids).update();
        });
    }

    @Override
    public Map<DocumentId, Integer> pageCounts(CompanyScope scope, ProjectVisibility visibility,
            Collection<DocumentId> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT p.document_id, count(*) AS pages FROM document_page p JOIN document d ON d.id = p.document_id
                WHERE p.document_id IN (:ids) AND %s AND %s AND %s GROUP BY p.document_id""".formatted(
                Sql.inScope("p.company_id"), Sql.inVisibleProjects("d.project_id"),
                Sql.notWithdrawn("d.withdrawn_at"));
        return tx.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility)
                .param("ids", ids.stream().map(DocumentId::value).toList())
                .query((rs, n) -> Map.entry(new DocumentId(Sql.uuid(rs, "document_id")), rs.getInt("pages"))).list())
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public int countEntries(CompanyId companyId, DocumentId caseId) {
        // cuenta también las relaciones con documentos retirados: siguen ahí aunque no se vean
        return tx.includeWithdrawn(true).inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                SELECT count(*) FROM case_file_entry WHERE company_id = :company AND case_document_id = :case""")
                .param("company", companyId.value()).param("case", caseId.value()).query(Integer.class).single());
    }
}
