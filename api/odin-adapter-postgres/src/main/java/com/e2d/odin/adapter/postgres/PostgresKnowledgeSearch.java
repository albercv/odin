package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.KnowledgeSearch;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.knowledge.ReviewStatus;
import com.e2d.odin.domain.search.CasePage;
import com.e2d.odin.domain.search.CasePageSelection;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.search.SearchResult;
import com.e2d.odin.domain.search.SimilarCase;
import com.e2d.odin.domain.search.SimilarCasesQuery;
import com.e2d.odin.domain.search.SimilarCasesResult;
import com.e2d.odin.domain.search.SourceType;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

public class PostgresKnowledgeSearch implements KnowledgeSearch {

    private static final String CANDIDATES = """
            SELECT p.document_id AS id, 'ORIGINAL_DOCUMENT' AS source, d.title, p.number AS page, d.paginated, d.case_document,
                   d.project_id, concat_ws(' ', u.first_name, u.last_name) AS author, d.uploaded_at AS date, p.text AS body,
                   %1$s + %15$s AS rank, d.superseded_by, d.company_id, NULL AS review_status
            FROM document_page p JOIN document d ON d.id = p.document_id JOIN app_user u ON u.id = d.uploaded_by,
                 q
            WHERE d.status = 'READY'
              AND %9$s AND %5$s AND %7$s AND %12$s AND %13$s
              AND (p.tsv @@ q.q OR %2$s)
            UNION ALL
            SELECT c.id, 'KNOWLEDGE_CARD', c.title, NULL, false, false, c.project_id, concat_ws(' ', u.first_name, u.last_name), c.created_at, c.body,
                   %3$s + %16$s, NULL::uuid, c.company_id, c.status
            FROM knowledge_card c JOIN app_user u ON u.id = c.author_id, q
            WHERE %11$s
              AND %10$s AND %6$s AND %8$s AND %14$s
              AND (c.tsv @@ q.q OR %4$s)""";
    /** Un candidato por página de documento marcado como caso y por ficha aprobada; sin autor a propósito (D-040). */
    private static final String CASE_CANDIDATES = """
            SELECT p.document_id AS id, 'ORIGINAL_DOCUMENT' AS source, d.title, p.number AS page, d.project_id,
                   d.uploaded_at AS date, p.text AS body,
                   %1$s + ts_rank_cd(p.tsv, q.q)::float8 AS rank, d.superseded_by, d.company_id, NULL AS review_status
            FROM document_page p JOIN document d ON d.id = p.document_id, q
            WHERE d.status = 'READY' AND d.case_document
              AND %5$s AND %6$s AND %7$s AND %12$s
              AND (p.tsv @@ q.q OR %2$s)
            UNION ALL
            SELECT c.id, 'KNOWLEDGE_CARD', c.title, NULL, c.project_id, c.created_at, c.body,
                   %3$s + ts_rank_cd(c.tsv, q.q)::float8, NULL::uuid, c.company_id, c.status
            FROM knowledge_card c, q
            WHERE %11$s
              AND %8$s AND %9$s AND %10$s
              AND (c.tsv @@ q.q OR %4$s)""";
    private static final String PAGES_OF_CASES = """
            SELECT p.document_id, p.number, p.text
            FROM document_page p JOIN document d ON d.id = p.document_id
            WHERE p.document_id = ANY (CAST(string_to_array(:ids, ',') AS uuid[])) AND %s AND %s AND %s
            ORDER BY p.document_id, p.number""";
    private final ScopedTransactions tx;

    public PostgresKnowledgeSearch(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        List<String> terms = SearchSql.usableTerms(query.terms());
        if (terms.isEmpty()) {
            return SearchResult.noSource();
        }
        List<String> exact = SearchSql.exactTokens(terms);
        List<SearchHit> hits = tx.inScope(query.scope(), query.visibility(), jdbc -> rows(jdbc, terms, exact, query));
        return new SearchResult(hits);
    }

    @Override
    public SimilarCasesResult findSimilarCases(SimilarCasesQuery query) {
        List<String> terms = SearchSql.usableTerms(query.terms());
        if (terms.isEmpty()) {
            return new SimilarCasesResult(List.of());
        }
        List<String> exact = SearchSql.exactTokens(terms);
        return tx.inScope(query.scope(), query.visibility(), jdbc -> {
            List<CaseRow> rows = caseRows(jdbc, terms, exact, query);
            Map<UUID, List<CasePage>> pages = pagesOf(jdbc, rows, query);
            return new SimilarCasesResult(rows.stream().map(row -> row.toCase(pages, terms)).toList());
        });
    }

    private record CaseRow(SourceType type, UUID id, String title, Optional<ProjectId> projectId, Instant addedAt,
            Optional<Integer> bestPage, String body, String headline, boolean superseded,
            Optional<DocumentRef> replacement, Optional<ReviewStatus> reviewStatus) {

        SimilarCase toCase(Map<UUID, List<CasePage>> pages, List<String> terms) {
            String excerpt = SearchSql.excerpt(headline, body, terms);
            if (type == SourceType.KNOWLEDGE_CARD) {
                return new SimilarCase(type, id, title, projectId, addedAt, 0, List.of(), Optional.of(body), false,
                        excerpt, superseded, replacement, reviewStatus);
            }
            List<CasePage> all = pages.getOrDefault(id, List.of());
            var selection = CasePageSelection.select(all, bestPage.orElse(1));
            return new SimilarCase(type, id, title, projectId, addedAt, all.size(), selection.pages(), Optional.empty(),
                    selection.truncated(), excerpt, superseded, replacement, Optional.empty());
        }
    }

    private List<CaseRow> caseRows(JdbcClient jdbc, List<String> terms, List<String> exact, SimilarCasesQuery query) {
        List<String> queryTerms = SearchSql.withNumberVariants(terms);
        String inner = "SELECT id, source, title, page, project_id, date, body, rank, superseded_by, company_id, review_status,"
                + " ts_headline('odin_spanish', body, q.q, 'StartSel=\"\", StopSel=\"\", MaxFragments=1') AS headline"
                + " FROM best, q ORDER BY rank DESC, title, id LIMIT :limit";
        String sql = "WITH q AS (SELECT " + SearchSql.tsquery(queryTerms.size()) + " AS q), cand AS ("
                + caseCandidates(exact.size()) + "), best AS (SELECT DISTINCT ON (id) * FROM cand"
                + " ORDER BY id, rank DESC, page NULLS FIRST) " + SearchSql.withSupersession(inner, "t.id");
        StatementSpec spec = Sql.visible(Sql.scoped(jdbc.sql(sql), query.scope()), query.visibility())
                .param("project", query.projectId().map(ProjectId::value).orElse(null))
                .param("limit", query.limit()).param("searcher", query.searcher().value());
        return SearchSql.bindTerms(spec, queryTerms, exact).query(PostgresKnowledgeSearch::caseRow).list();
    }

    private static String caseCandidates(int exactCount) {
        return CASE_CANDIDATES.formatted(SearchSql.boost("p.text", exactCount),
                SearchSql.anyMatch("p.text", exactCount), SearchSql.boost("c.body", exactCount),
                SearchSql.anyMatch("c.body", exactCount), SearchSql.inProject("d.project_id"),
                Sql.inScope("d.company_id"), Sql.inVisibleProjects("d.project_id"),
                SearchSql.inProject("c.project_id"), Sql.inScope("c.company_id"),
                Sql.inVisibleProjects("c.project_id"), SearchSql.visibleCards("c"),
                Sql.listable("d"));
    }

    private static CaseRow caseRow(ResultSet rs, int n) throws SQLException {
        int page = rs.getInt("page");
        return new CaseRow(SourceType.valueOf(rs.getString("source")), Sql.uuid(rs, "id"), rs.getString("title"),
                Optional.ofNullable(Sql.uuid(rs, "project_id")).map(ProjectId::new), Sql.instant(rs, "date"),
                rs.wasNull() ? Optional.empty() : Optional.of(page), rs.getString("body"), rs.getString("headline"),
                Sql.uuid(rs, "superseded_by") != null, SupersessionSql.current(rs), reviewStatus(rs));
    }

    /** Las páginas de los documentos elegidos, con el mismo filtro de empresa y de proyectos que la consulta. */
    private Map<UUID, List<CasePage>> pagesOf(JdbcClient jdbc, List<CaseRow> rows, SimilarCasesQuery query) {
        String ids = rows.stream().filter(r -> r.type() == SourceType.ORIGINAL_DOCUMENT).map(r -> r.id().toString())
                .collect(Collectors.joining(","));
        if (ids.isEmpty()) {
            return Map.of();
        }
        String sql = PAGES_OF_CASES.formatted(Sql.inScope("p.company_id"), Sql.inVisibleProjects("d.project_id"),
                Sql.listable("d"));
        Map<UUID, List<CasePage>> pages = new HashMap<>();
        Sql.visible(Sql.scoped(jdbc.sql(sql), query.scope()), query.visibility()).param("ids", ids)
                .query((rs, n) -> {
                    pages.computeIfAbsent(Sql.uuid(rs, "document_id"), k -> new ArrayList<>())
                            .add(new CasePage(rs.getInt("number"), rs.getString("text")));
                    return n;
                }).list();
        return pages;
    }

    private List<SearchHit> rows(JdbcClient jdbc, List<String> terms, List<String> exact, SearchQuery query) {
        List<String> queryTerms = SearchSql.withNumberVariants(terms);
        String inner = "SELECT id, source, title, page, paginated, case_document, project_id, author, date, body, rank,"
                + " superseded_by, company_id, review_status, ts_headline('odin_spanish', body, q.q, 'StartSel=\"\", StopSel=\"\","
                + " MaxFragments=1') AS headline FROM (" + candidates(exact.size()) + ") r, q"
                + " ORDER BY rank DESC, title, page NULLS FIRST, id LIMIT :limit";
        String sql = "WITH q AS (SELECT " + SearchSql.tsquery(queryTerms.size()) + " AS q), "
                + SearchSql.lexemes(queryTerms.size()) + " " + SearchSql.withSupersession(inner, "t.page NULLS FIRST, t.id");
        StatementSpec spec = Sql.visible(Sql.scoped(jdbc.sql(sql), query.scope()), query.visibility())
                .param("project", query.projectId().map(ProjectId::value).orElse(null))
                .param("caseId", query.caseId().map(DocumentId::value).orElse(null))
                .param("limit", query.limit()).param("searcher", query.searcher().value());
        return SearchSql.bindTerms(spec, queryTerms, exact).query((rs, n) -> hit(rs, terms)).list();
    }

    private static String candidates(int exactCount) {
        String bodyMatch = SearchSql.anyMatch("p.text", exactCount);
        String cardMatch = SearchSql.anyMatch("c.body", exactCount);
        return CANDIDATES.formatted(SearchSql.boost("p.text", exactCount), bodyMatch,
                SearchSql.boost("c.body", exactCount), cardMatch, Sql.inScope("d.company_id"),
                Sql.inScope("c.company_id"), Sql.inVisibleProjects("d.project_id"),
                Sql.inVisibleProjects("c.project_id"), SearchSql.inProject("d.project_id"),
                SearchSql.inProject("c.project_id"), SearchSql.visibleCards("c"),
                Sql.listable("d"), SearchSql.inCaseFile("d"), SearchSql.NO_CASE_FILE, SearchSql.rank("p.tsv"),
                SearchSql.rank("c.tsv"));
    }

    private static SearchHit hit(ResultSet rs, List<String> terms) throws SQLException {
        int page = rs.getInt("page");
        Optional<Integer> pageNumber = rs.wasNull() ? Optional.empty() : Optional.of(page);
        String body = rs.getString("body");
        return new SearchHit(SourceType.valueOf(rs.getString("source")), Sql.uuid(rs, "id"), rs.getString("title"),
                pageNumber, rs.getBoolean("paginated"), rs.getBoolean("case_document"),
                Optional.ofNullable(Sql.uuid(rs, "project_id")).map(ProjectId::new), rs.getString("author"),
                Sql.instant(rs, "date"), SearchSql.excerpt(rs.getString("headline"), body, terms),
                rs.getDouble("rank"), Sql.uuid(rs, "superseded_by") != null, SupersessionSql.current(rs),
                reviewStatus(rs));
    }

    private static Optional<ReviewStatus> reviewStatus(ResultSet rs) throws SQLException {
        return Optional.ofNullable(rs.getString("review_status")).map(ReviewStatus::valueOf);
    }
}
