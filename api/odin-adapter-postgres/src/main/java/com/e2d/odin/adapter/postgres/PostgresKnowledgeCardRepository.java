package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.KnowledgeCardRepository;
import com.e2d.odin.domain.knowledge.CardReview;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.knowledge.ReviewDecision;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

public class PostgresKnowledgeCardRepository implements KnowledgeCardRepository {

    private static final String COLUMNS = """
            id, company_id, project_id, title, body, original_body, author_id, created_at, status,
            reviewer_id, decision, reviewed_at, review_note""";
    private final ScopedTransactions tx;

    public PostgresKnowledgeCardRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public void save(KnowledgeCard c) {
        UUID reviewer = c.review().map(r -> r.reviewerId().value()).orElse(null);
        tx.runInScope(CompanyScope.of(c.companyId()), ProjectVisibility.all(), jdbc -> jdbc.sql("""
                INSERT INTO knowledge_card (id, company_id, project_id, title, body, original_body, author_id,
                    created_at, status, reviewer_id, decision, reviewed_at, review_note)
                VALUES (:id, :company, :project, :title, :body, :original, :author, :created, :status,
                    :reviewer, :decision, :reviewed, :note)
                ON CONFLICT (id) DO UPDATE SET title = :title, body = :body, original_body = :original,
                    status = :status, reviewer_id = :reviewer, decision = :decision, reviewed_at = :reviewed,
                    review_note = :note""")
                .param("id", c.id().value()).param("company", c.companyId().value())
                .param("project", c.projectId().map(ProjectId::value).orElse(null)).param("title", c.title())
                .param("body", c.body()).param("original", c.originalBody().orElse(null))
                .param("author", c.authorId().value()).param("created", Sql.ts(c.createdAt()))
                .param("status", c.status().name()).param("reviewer", reviewer)
                .param("decision", c.review().map(r -> r.decision().name()).orElse(null))
                .param("reviewed", c.review().map(r -> Sql.ts(r.reviewedAt())).orElse(null))
                .param("note", c.review().flatMap(CardReview::note).orElse(null)).update());
    }

    @Override
    public Optional<KnowledgeCard> findForReview(CompanyScope scope, ProjectVisibility visibility, CardId id) {
        String sql = "SELECT " + COLUMNS + " FROM knowledge_card WHERE id = :id AND " + Sql.inScope("company_id")
                + " AND " + Sql.inVisibleProjects("project_id");
        return tx.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility)
                .param("id", id.value()).query((rs, n) -> card(rs)).optional());
    }

    @Override
    public List<KnowledgeCard> listByStatus(CompanyScope scope, ProjectVisibility visibility, CardStatus status) {
        String sql = "SELECT " + COLUMNS + " FROM knowledge_card WHERE status = :status AND "
                + Sql.inScope("company_id") + " AND " + Sql.inVisibleProjects("project_id") + " ORDER BY seq";
        return tx.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility)
                .param("status", status.name()).query((rs, n) -> card(rs)).list());
    }

    @Override
    public List<KnowledgeCard> search(CompanyScope scope, ProjectVisibility visibility, CardStatus status,
            Optional<String> query, int offset, int limit) {
        String sql = "SELECT " + COLUMNS + " FROM knowledge_card WHERE " + searchFilter()
                + " ORDER BY seq OFFSET :offset LIMIT :limit";
        return tx.inScope(scope, visibility, jdbc -> bindSearch(Sql.scoped(jdbc.sql(sql), scope), visibility, status,
                query).param("offset", offset).param("limit", limit).query((rs, n) -> card(rs)).list());
    }

    @Override
    public int count(CompanyScope scope, ProjectVisibility visibility, CardStatus status, Optional<String> query) {
        String sql = "SELECT count(*) FROM knowledge_card WHERE " + searchFilter();
        return tx.inScope(scope, visibility, jdbc -> bindSearch(Sql.scoped(jdbc.sql(sql), scope), visibility, status,
                query).query(Integer.class).single());
    }

    private static String searchFilter() {
        return "status = :status AND (CAST(:text AS text) IS NULL OR unaccent(lower(title || ' ' || body)) "
                + "LIKE unaccent(lower(CAST(:text AS text)))) AND " + Sql.inScope("company_id") + " AND "
                + Sql.inVisibleProjects("project_id");
    }

    private static StatementSpec bindSearch(StatementSpec spec, ProjectVisibility visibility, CardStatus status,
            Optional<String> query) {
        String like = query.map(q -> "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                + "%").orElse(null);
        return Sql.visible(spec, visibility).param("status", status.name()).param("text", like);
    }

    @Override
    public List<KnowledgeCard> listByAuthor(CompanyScope scope, ProjectVisibility visibility, UserId authorId,
            Optional<CardStatus> status, int limit) {
        String sql = "SELECT " + COLUMNS + " FROM knowledge_card WHERE author_id = :author AND "
                + "(CAST(:status AS text) IS NULL OR status = :status) AND " + Sql.inScope("company_id") + " AND "
                + Sql.inVisibleProjects("project_id") + " ORDER BY created_at DESC, seq DESC LIMIT :limit";
        return tx.inScope(scope, visibility, jdbc -> Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility)
                .param("author", authorId.value()).param("status", status.map(Enum::name).orElse(null))
                .param("limit", limit).query((rs, n) -> card(rs)).list());
    }

    private static KnowledgeCard card(ResultSet rs) throws SQLException {
        return new KnowledgeCard(new CardId(Sql.uuid(rs, "id")), new CompanyId(Sql.uuid(rs, "company_id")),
                Optional.ofNullable(Sql.uuid(rs, "project_id")).map(ProjectId::new), rs.getString("title"),
                rs.getString("body"), Optional.ofNullable(rs.getString("original_body")),
                new UserId(Sql.uuid(rs, "author_id")), Sql.instant(rs, "created_at"),
                CardStatus.valueOf(rs.getString("status")), review(rs));
    }

    private static Optional<CardReview> review(ResultSet rs) throws SQLException {
        UUID reviewer = Sql.uuid(rs, "reviewer_id");
        if (reviewer == null) {
            return Optional.empty();
        }
        return Optional.of(new CardReview(new UserId(reviewer), ReviewDecision.valueOf(rs.getString("decision")),
                Sql.instant(rs, "reviewed_at"), Optional.ofNullable(rs.getString("review_note"))));
    }
}
