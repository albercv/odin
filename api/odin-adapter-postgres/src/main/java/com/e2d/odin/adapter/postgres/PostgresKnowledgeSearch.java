package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.KnowledgeSearch;
import com.e2d.odin.domain.search.QuoteMatcher;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.search.SearchResult;
import com.e2d.odin.domain.search.SourceType;
import com.e2d.odin.domain.shared.ProjectId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

public class PostgresKnowledgeSearch implements KnowledgeSearch {

    private static final int EXCERPT_CONTEXT = 150;
    private static final Pattern IDENTIFIER = Pattern.compile("(?=.*\\d)(?=.*[\\p{L}\\-/.]).{5,}");
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$");
    private static final int MAX_IDENTIFIERS = 10;
    private static final String CANDIDATES = """
            SELECT p.document_id AS id, 'ORIGINAL_DOCUMENT' AS source, d.title, p.number AS page, d.paginated,
                   d.project_id, u.display_name AS author, d.uploaded_at AS date, p.text AS body,
                   %1$s + ts_rank_cd(p.tsv, q.q)::float8 AS rank
            FROM document_page p JOIN document d ON d.id = p.document_id JOIN app_user u ON u.id = d.uploaded_by,
                 q
            WHERE d.status = 'READY' AND (CAST(:project AS uuid) IS NULL OR d.project_id = :project)
              AND (p.tsv @@ q.q OR %2$s)
            UNION ALL
            SELECT c.id, 'KNOWLEDGE_CARD', c.title, NULL, false, c.project_id, u.display_name, c.created_at, c.body,
                   %3$s + ts_rank_cd(c.tsv, q.q)::float8
            FROM knowledge_card c JOIN app_user u ON u.id = c.author_id, q
            WHERE c.status = 'APPROVED' AND (CAST(:project AS uuid) IS NULL OR c.project_id = :project)
              AND (c.tsv @@ q.q OR %4$s)""";
    private final ScopedTransactions tx;

    public PostgresKnowledgeSearch(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        List<String> terms = query.terms().stream().filter(t -> t != null && !t.isBlank()).toList();
        if (terms.isEmpty()) {
            return SearchResult.noSource();
        }
        List<String> exact = exactTokens(terms);
        List<SearchHit> hits = tx.inScope(query.scope(), jdbc -> rows(jdbc, terms, exact, query));
        return new SearchResult(hits);
    }

    private List<SearchHit> rows(JdbcClient jdbc, List<String> terms, List<String> exact, SearchQuery query) {
        String sql = "WITH q AS (SELECT " + tsquery(terms.size()) + " AS q) SELECT id, source, title, page, paginated,"
                + " project_id, author, date, body, rank, ts_headline('odin_spanish', body, q.q,"
                + " 'StartSel=\"\", StopSel=\"\", MaxFragments=1') AS headline FROM (" + candidates(exact.size())
                + ") r, q ORDER BY rank DESC, title, page NULLS FIRST, id LIMIT :limit";
        StatementSpec spec = jdbc.sql(sql).param("project", query.projectId().map(ProjectId::value).orElse(null))
                .param("limit", query.limit());
        for (int i = 0; i < terms.size(); i++) {
            spec = spec.param("t" + i, terms.get(i));
        }
        for (int i = 0; i < exact.size(); i++) {
            spec = spec.param("x" + i, "%" + exact.get(i).replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_") + "%");
        }
        return spec.query((rs, n) -> hit(rs, terms)).list();
    }

    private static String candidates(int exactCount) {
        String bodyMatch = anyMatch("p.text", exactCount);
        String cardMatch = anyMatch("c.body", exactCount);
        return CANDIDATES.formatted(boost("p.text", exactCount), bodyMatch, boost("c.body", exactCount), cardMatch);
    }

    private static String tsquery(int termCount) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < termCount; i++) {
            parts.add("replace(plainto_tsquery('odin_spanish', :t" + i + ")::text, '&', '|')::tsquery");
        }
        return String.join(" || ", parts);
    }

    private static String anyMatch(String column, int exactCount) {
        List<String> parts = new ArrayList<>(List.of("false"));
        for (int i = 0; i < exactCount; i++) {
            parts.add(column + " ILIKE :x" + i);
        }
        return String.join(" OR ", parts);
    }

    private static String boost(String column, int exactCount) {
        return "(CASE WHEN " + anyMatch(column, exactCount) + " THEN 100 ELSE 0 END)";
    }

    private static List<String> exactTokens(List<String> terms) {
        return terms.stream().flatMap(t -> Arrays.stream(t.trim().split("\s+")))
                .map(token -> EDGE_PUNCTUATION.matcher(token).replaceAll(""))
                .filter(token -> IDENTIFIER.matcher(token).matches()).distinct().limit(MAX_IDENTIFIERS).toList();
    }

    private static SearchHit hit(ResultSet rs, List<String> terms) throws SQLException {
        int page = rs.getInt("page");
        Optional<Integer> pageNumber = rs.wasNull() ? Optional.empty() : Optional.of(page);
        String body = rs.getString("body");
        return new SearchHit(SourceType.valueOf(rs.getString("source")), Sql.uuid(rs, "id"), rs.getString("title"),
                pageNumber, rs.getBoolean("paginated"),
                Optional.ofNullable(Sql.uuid(rs, "project_id")).map(ProjectId::new), rs.getString("author"),
                Sql.instant(rs, "date"), excerpt(rs.getString("headline"), body, terms), rs.getDouble("rank"));
    }

    private static String excerpt(String headline, String body, List<String> terms) {
        if (QuoteMatcher.containsLiteral(body, headline)) {
            return headline;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        int first = terms.stream().flatMap(t -> Arrays.stream(t.toLowerCase(Locale.ROOT).split("\\s+")))
                .filter(w -> w.length() >= 3).mapToInt(lower::indexOf).filter(i -> i >= 0).min().orElse(0);
        int start = Math.max(0, first - EXCERPT_CONTEXT);
        return body.substring(start, Math.min(body.length(), start + 2 * EXCERPT_CONTEXT)).strip();
    }
}
