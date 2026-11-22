package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.DocumentLinkRepository;
import com.e2d.odin.domain.citation.DocumentCitation;
import com.e2d.odin.domain.citation.OwnIdentifier;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class PostgresDocumentLinkRepository implements DocumentLinkRepository {

    /** Lo que cuenta como destino: leído, ni retirado ni sin colocar, y no un escaneo enlazado a un digital. */
    private static final String LISTABLE = "t.status = 'READY' AND t.withdrawn_at IS NULL AND NOT t.intake_pending "
            + "AND t.scan_of IS NULL";
    private static final String PENDING = """
            SELECT d.company_id, d.id FROM document d LEFT JOIN document_link_scan s ON s.document_id = d.id
            WHERE d.status = 'READY' AND d.withdrawn_at IS NULL AND NOT d.intake_pending AND d.scan_of IS NULL
              AND (s.document_id IS NULL OR s.extractor_version <> :version)
            ORDER BY d.seq LIMIT :limit""";
    /**
     * Un vínculo detectado se resuelve si y solo si hay exactamente un destino con ese identificador: de la empresa del
     * origen y del proyecto del origen o general. Es función de los identificadores actuales.
     */
    private static final String RESOLVE = """
            UPDATE document_link l SET target_document_id = r.target
            FROM (SELECT k.id, (SELECT (array_agg(i.document_id))[1] FROM document_identifier i
                        JOIN document t ON t.id = i.document_id AND t.company_id = i.company_id
                        WHERE i.company_id = k.company_id AND i.identifier = k.cited_identifier
                          AND i.document_id <> k.source_document_id AND %s
                          AND (t.project_id IS NULL OR t.project_id = s.project_id)
                        HAVING count(*) = 1) AS target
                  FROM document_link k JOIN document s ON s.id = k.source_document_id
                  WHERE k.company_id = :company AND k.origin = 'DETECTED') r
            WHERE l.id = r.id AND l.target_document_id IS DISTINCT FROM r.target""".formatted(LISTABLE);

    private final ScopedTransactions tx;
    /** Ve también lo retirado para poder soltar un vínculo cuyo destino se retiró. */
    private final ScopedTransactions pipeline;

    public PostgresDocumentLinkRepository(ScopedTransactions tx) {
        this.tx = tx;
        this.pipeline = tx.includeWithdrawn(true);
    }

    @Override
    public List<Pending> pending(String extractorVersion, int limit) {
        return pipeline.inScope(CompanyScope.all(), ProjectVisibility.all(), jdbc -> jdbc.sql(PENDING)
                .param("version", extractorVersion).param("limit", limit)
                .query((rs, n) -> new Pending(new CompanyId(Sql.uuid(rs, "company_id")),
                        new DocumentId(Sql.uuid(rs, "id")))).list());
    }

    @Override
    public List<DocumentPage> pagesOf(Pending document) {
        return tx.inScope(CompanyScope.of(document.companyId()), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                SELECT document_id, number, text, reader_name, reader_version FROM document_page
                WHERE document_id = :id AND company_id = :company ORDER BY number""")
                .param("id", document.documentId().value()).param("company", document.companyId().value())
                .query((rs, n) -> new DocumentPage(document.documentId(), rs.getInt("number"), rs.getString("text"),
                        rs.getString("reader_name"), rs.getString("reader_version"))).list());
    }

    @Override
    public void save(Pending document, String extractorVersion, List<OwnIdentifier> identifiers,
            List<DocumentCitation> citations, Instant now) {
        pipeline.runInScope(CompanyScope.of(document.companyId()), ProjectVisibility.all(), jdbc -> {
            replaceFindings(jdbc, document, identifiers, citations, now);
            jdbc.sql("""
                    INSERT INTO document_link_scan (company_id, document_id, extractor_version, scanned_at)
                    VALUES (:company, :id, :version, :now)
                    ON CONFLICT (document_id) DO UPDATE SET extractor_version = :version, scanned_at = :now""")
                    .param("company", document.companyId().value()).param("id", document.documentId().value())
                    .param("version", extractorVersion).param("now", Sql.ts(now)).update();
            jdbc.sql(RESOLVE).param("company", document.companyId().value()).update();
        });
    }

    private static void replaceFindings(JdbcClient jdbc, Pending document, List<OwnIdentifier> identifiers,
            List<DocumentCitation> citations, Instant now) {
        UUID id = document.documentId().value();
        UUID company = document.companyId().value();
        jdbc.sql("DELETE FROM document_identifier WHERE document_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM document_link WHERE source_document_id = :id AND origin = 'DETECTED'")
                .param("id", id).update();
        for (OwnIdentifier own : identifiers) {
            jdbc.sql("INSERT INTO document_identifier (company_id, document_id, identifier, page_number) "
                    + "VALUES (:company, :id, :identifier, :page)").param("company", company).param("id", id)
                    .param("identifier", own.identifier()).param("page", own.page()).update();
        }
        for (DocumentCitation citation : citations) {
            jdbc.sql("""
                    INSERT INTO document_link (id, company_id, source_document_id, relation, cited_identifier,
                        evidence_page, evidence_quote, origin, created_at)
                    VALUES (gen_random_uuid(), :company, :id, :relation, :cited, :page, :quote, 'DETECTED', :now)""")
                    .param("company", company).param("id", id).param("relation", citation.relation().name())
                    .param("cited", citation.citedIdentifier()).param("page", citation.page())
                    .param("quote", citation.quote()).param("now", Sql.ts(now)).update();
        }
    }
}
