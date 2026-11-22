package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.DocumentLinkReview;
import com.e2d.odin.domain.citation.LinkRelation;
import com.e2d.odin.domain.citation.LinkStatus;
import com.e2d.odin.domain.context.CorrectionDirection;
import com.e2d.odin.domain.context.PageText;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PostgresDocumentLinkReview implements DocumentLinkReview {

    private static final String FIND = "SELECT id, company_id, source_document_id, target_document_id, relation, status "
            + "FROM document_link WHERE id = :id AND " + Sql.inScope("company_id");
    private static final String LINKS = """
            SELECT l.id, l.source_document_id, l.target_document_id, l.relation, l.status, l.origin,
                   l.cited_identifier, l.evidence_page, l.evidence_quote, s.title AS source_title, t.title AS target_title
            FROM document_link l
            JOIN document s ON s.id = l.source_document_id
            LEFT JOIN document t ON t.id = l.target_document_id
            WHERE %s AND (l.source_document_id = :document OR l.target_document_id = :document)
            ORDER BY l.relation, l.cited_identifier, l.id""".formatted(Sql.inScope("l.company_id"));

    private final ScopedTransactions tx;

    public PostgresDocumentLinkReview(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public Optional<Link> find(CompanyScope scope, UUID linkId) {
        return tx.inScope(scope, ProjectVisibility.all(), jdbc -> Sql.scoped(jdbc.sql(FIND), scope).param("id", linkId)
                .query((rs, n) -> new Link(linkId, new CompanyId(Sql.uuid(rs, "company_id")),
                        new DocumentId(Sql.uuid(rs, "source_document_id")),
                        Optional.ofNullable(Sql.uuid(rs, "target_document_id")).map(DocumentId::new),
                        LinkRelation.valueOf(rs.getString("relation")), LinkStatus.valueOf(rs.getString("status"))))
                .optional());
    }

    @Override
    public List<LinkView> linksOf(CompanyScope scope, DocumentId document) {
        return tx.inScope(scope, ProjectVisibility.all(), jdbc -> Sql.scoped(jdbc.sql(LINKS), scope)
                .param("document", document.value()).query((rs, n) -> view(rs, document)).list());
    }

    private static LinkView view(ResultSet rs, DocumentId document) throws SQLException {
        boolean cites = document.value().equals(Sql.uuid(rs, "source_document_id"));
        UUID other = Sql.uuid(rs, cites ? "target_document_id" : "source_document_id");
        String title = rs.getString(cites ? "target_title" : "source_title");
        return new LinkView(Sql.uuid(rs, "id"),
                cites ? CorrectionDirection.CORRECTS : CorrectionDirection.CORRECTED_BY,
                LinkRelation.valueOf(rs.getString("relation")), LinkStatus.valueOf(rs.getString("status")),
                rs.getString("origin"), Optional.ofNullable(other).map(id -> new DocumentRef(new DocumentId(id), title)),
                rs.getString("cited_identifier"), new PageText(rs.getInt("evidence_page"), rs.getString("evidence_quote")));
    }

    @Override
    public void setStatus(CompanyId company, UUID linkId, LinkStatus status, UserId reviewer, Instant at) {
        tx.runInScope(CompanyScope.of(company), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                UPDATE document_link SET status = :status, reviewed_by = :by, reviewed_at = :at
                WHERE id = :id AND company_id = :company""").param("status", status.name())
                .param("by", reviewer.value()).param("at", Sql.ts(at)).param("id", linkId)
                .param("company", company.value()).update());
    }
}
