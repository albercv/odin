package com.e2d.odin.adapter.postgres;

import com.e2d.odin.domain.search.QuoteMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

/** Las piezas de la consulta de búsqueda que comparten la búsqueda normal y los casos parecidos. */
final class SearchSql {

    private static final int EXCERPT_CONTEXT = 150;
    private static final Pattern IDENTIFIER = Pattern.compile("(?=.*\\d)(?=.*[\\p{L}\\-/.]).{5,}");
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$");
    private static final int MAX_IDENTIFIERS = 10;
    /** Los sustituidos van detrás de los vigentes, sin cambiar el orden entre ellos. */
    private static final String SUPERSEDED_LAST = " ORDER BY t.superseded_by IS NOT NULL, t.rank DESC, t.title, ";

    private SearchSql() {
    }

    static List<String> usableTerms(List<String> terms) {
        return terms.stream().filter(t -> t != null && !t.isBlank()).toList();
    }

    static String tsquery(int termCount) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < termCount; i++) {
            parts.add("replace(plainto_tsquery('odin_spanish', :t" + i + ")::text, '&', '|')::tsquery");
        }
        return String.join(" || ", parts);
    }

    static String anyMatch(String column, int exactCount) {
        List<String> parts = new ArrayList<>(List.of("false"));
        for (int i = 0; i < exactCount; i++) {
            parts.add(column + " ILIKE :x" + i);
        }
        return String.join(" OR ", parts);
    }

    static String boost(String column, int exactCount) {
        return "(CASE WHEN " + anyMatch(column, exactCount) + " THEN 100 ELSE 0 END)";
    }

    static List<String> exactTokens(List<String> terms) {
        return terms.stream().flatMap(t -> Arrays.stream(t.trim().split("\\s+")))
                .map(token -> EDGE_PUNCTUATION.matcher(token).replaceAll(""))
                .filter(token -> IDENTIFIER.matcher(token).matches()).distinct().limit(MAX_IDENTIFIERS).toList();
    }

    /** Filtro opcional por un proyecto: incluye lo que no tiene proyecto (D-053). */
    static String inProject(String projectColumn) {
        return "(CAST(:project AS uuid) IS NULL OR " + projectColumn + " = :project OR " + projectColumn
                + " IS NULL)";
    }

    /** Las fichas que puede ver quien busca (:searcher): las aprobadas y sus propias pendientes. Las rechazadas, nunca. */
    static String visibleCards(String alias) {
        return "(" + alias + ".status = 'APPROVED' OR (" + alias + ".status = 'PENDING' AND " + alias
                + ".author_id = :searcher))";
    }

    static StatementSpec bindTerms(StatementSpec spec, List<String> terms, List<String> exact) {
        StatementSpec bound = spec;
        for (int i = 0; i < terms.size(); i++) {
            bound = bound.param("t" + i, terms.get(i));
        }
        for (int i = 0; i < exact.size(); i++) {
            bound = bound.param("x" + i, "%" + exact.get(i).replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_") + "%");
        }
        return bound;
    }

    /**
     * Envuelve la consulta interior (alias {@code t}, con rank, title, superseded_by y company_id) con la resolución del
     * sustituto, bajo el filtro de empresa y de proyectos del que lee, y con los sustituidos detrás de los vigentes.
     */
    static String withSupersession(String inner, String orderTail) {
        return "SELECT t.*, " + SupersessionSql.COLUMNS + " FROM (" + inner + ") t "
                + SupersessionSql.joins("t.superseded_by", "t.company_id") + SUPERSEDED_LAST + orderTail;
    }

    static String excerpt(String headline, String body, List<String> terms) {
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
