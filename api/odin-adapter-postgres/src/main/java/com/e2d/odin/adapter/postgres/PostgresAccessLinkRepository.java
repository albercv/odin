package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.AccessLinkRepository;
import com.e2d.odin.domain.link.AccessLink;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class PostgresAccessLinkRepository implements AccessLinkRepository {

    private static final String COLUMNS = "id, kind, company_id, user_id, case_document_id, project_id, document_id, created_at, "
            + "expires_at, bound_address, upload_count";
    private final ScopedTransactions tx;

    public PostgresAccessLinkRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public void save(AccessLink l, String tokenHash) {
        tx.runInScope(CompanyScope.of(l.companyId()), jdbc -> jdbc.sql("""
                INSERT INTO access_link (id, kind, token_hash, company_id, user_id, case_document_id, project_id,
                    document_id, created_at, expires_at, bound_address, upload_count)
                VALUES (:id, :kind, :hash, :company, :user, :caseDocument, :project, :document, :created, :expires, :bound,
                    :uploads)""")
                .param("id", l.id()).param("kind", l.kind().name()).param("hash", tokenHash)
                .param("company", l.companyId().value()).param("user", l.userId().value())
                .param("caseDocument", l.caseId().map(DocumentId::value).orElse(null))
                .param("project", l.projectId().map(ProjectId::value).orElse(null))
                .param("document", l.documentId().map(DocumentId::value).orElse(null))
                .param("created", Sql.ts(l.createdAt())).param("expires", Sql.ts(l.expiresAt()))
                .param("bound", l.boundAddress().orElse(null)).param("uploads", l.uploadCount()).update());
    }

    @Override
    public Optional<AccessLink> findByTokenHash(String tokenHash) {
        return tx.inScope(CompanyScope.all(), jdbc -> jdbc.sql("SELECT " + COLUMNS
                + " FROM access_link WHERE token_hash = :hash").param("hash", tokenHash)
                .query((rs, n) -> link(rs)).optional());
    }

    @Override
    public Optional<String> bind(AccessLink link, String address) {
        return tx.inScope(CompanyScope.of(link.companyId()), jdbc -> {
            jdbc.sql("UPDATE access_link SET bound_address = :address WHERE id = :id AND bound_address IS NULL")
                    .param("address", address).param("id", link.id()).update();
            return jdbc.sql("SELECT bound_address FROM access_link WHERE id = :id").param("id", link.id())
                    .query(String.class).optional();
        });
    }

    @Override
    public boolean reserveUpload(AccessLink link, int max) {
        return tx.inScope(CompanyScope.of(link.companyId()), jdbc -> jdbc.sql("""
                UPDATE access_link SET upload_count = upload_count + 1
                WHERE id = :id AND upload_count < :max RETURNING upload_count""")
                .param("id", link.id()).param("max", max).query(Integer.class).optional().isPresent());
    }

    @Override
    public void deleteOf(CompanyId companyId, UserId userId) {
        tx.runInScope(CompanyScope.of(companyId), jdbc -> jdbc.sql(
                "DELETE FROM access_link WHERE company_id = :company AND user_id = :user")
                .param("company", companyId.value()).param("user", userId.value()).update());
    }

    private static AccessLink link(ResultSet rs) throws SQLException {
        return new AccessLink(rs.getObject("id", UUID.class), AccessLinkKind.valueOf(rs.getString("kind")),
                new CompanyId(Sql.uuid(rs, "company_id")), new UserId(Sql.uuid(rs, "user_id")),
                optionalDocument(rs, "case_document_id"),
                Optional.ofNullable(rs.getObject("project_id", UUID.class)).map(ProjectId::new),
                optionalDocument(rs, "document_id"),
                Sql.instant(rs, "created_at"), Sql.instant(rs, "expires_at"),
                Optional.ofNullable(rs.getString("bound_address")), rs.getInt("upload_count"));
    }

    private static Optional<DocumentId> optionalDocument(ResultSet rs, String column) throws SQLException {
        return Optional.ofNullable(rs.getObject(column, UUID.class)).map(DocumentId::new);
    }
}
