package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.CaseFileRepository;
import com.e2d.odin.domain.casefile.CaseFileAction;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;

public class PostgresCaseFileRepository implements CaseFileRepository {

    /** Primera barrera: empresa y proyectos visibles, y sin retirados, para los dos documentos de la relación. */
    private static final String VISIBLE = "AND " + Sql.inScope("x.company_id") + """
             AND EXISTS (SELECT 1 FROM document c WHERE c.id = x.case_document_id AND %s AND %s)
             AND EXISTS (SELECT 1 FROM document m WHERE m.id = x.document_id AND %s AND %s)""".formatted(
            Sql.inVisibleProjects("c.project_id"), Sql.notWithdrawn("c.withdrawn_at"),
            Sql.inVisibleProjects("m.project_id"), Sql.notWithdrawn("m.withdrawn_at"));

    private final ScopedTransactions tx;

    public PostgresCaseFileRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public boolean add(CaseFileEntry e) {
        return tx.inScope(CompanyScope.of(e.companyId()), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                INSERT INTO case_file_entry (company_id, case_document_id, document_id, added_by, added_at)
                VALUES (:company, :case, :document, :by, :at) ON CONFLICT DO NOTHING""")
                .param("company", e.companyId().value()).param("case", e.caseId().value())
                .param("document", e.documentId().value()).param("by", e.addedBy().value())
                .param("at", Sql.ts(e.addedAt())).update()) == 1;
    }

    @Override
    public boolean remove(CompanyId companyId, DocumentId caseId, DocumentId documentId) {
        return tx.inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                DELETE FROM case_file_entry WHERE company_id = :company AND case_document_id = :case
                AND document_id = :document""").param("company", companyId.value()).param("case", caseId.value())
                .param("document", documentId.value()).update()) == 1;
    }

    @Override
    public void record(CaseFileEvent e) {
        tx.runInScope(CompanyScope.of(e.companyId()), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                INSERT INTO case_file_event (id, company_id, case_document_id, document_id, action, actor_id, at)
                VALUES (:id, :company, :case, :document, :action, :actor, :at)""")
                .param("id", e.id()).param("company", e.companyId().value()).param("case", e.caseId().value())
                .param("document", e.documentId().value()).param("action", e.action().name())
                .param("actor", e.actorId().value()).param("at", Sql.ts(e.at())).update());
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

    @Override
    public int countEntries(CompanyId companyId, DocumentId caseId) {
        // cuenta también las relaciones con documentos retirados: siguen ahí aunque no se vean
        return tx.includeWithdrawn(true).inScope(CompanyScope.of(companyId), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                SELECT count(*) FROM case_file_entry WHERE company_id = :company AND case_document_id = :case""")
                .param("company", companyId.value()).param("case", caseId.value()).query(Integer.class).single());
    }
}
