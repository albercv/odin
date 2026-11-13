package com.e2d.odin.adapter.postgres;

import com.e2d.odin.domain.search.QuoteMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

/** Las piezas de la consulta de búsqueda que comparten la búsqueda normal y los casos parecidos. */
final class SearchSql {

    private static final int EXCERPT_CONTEXT = 150;
    private static final Pattern IDENTIFIER = Pattern.compile("(?=.*\\d)(?=.*[\\p{L}\\-/.]).{5,}");
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$");
    private static final Pattern NUMBER = Pattern.compile("\\d{3,}");
    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");
    private static final Pattern COMPOUND = Pattern.compile("(\\d+)[/-](\\d+)");
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
        Stream<String> identifiers = tokens(terms).filter(token -> IDENTIFIER.matcher(token).matches());
        // Las formas compuestas van después de los identificadores: MAX_IDENTIFIERS puede recortarlas.
        Stream<String> compounds = caseNumbers(terms).stream().flatMap(n -> Stream.of("-" + n, "/" + n, n + "/"));
        return Stream.concat(identifiers, compounds).distinct().limit(MAX_IDENTIFIERS).toList();
    }

    /** Los términos más las variantes de los números de caso, para la consulta de texto completo. */
    static List<String> withNumberVariants(List<String> terms) {
        return Stream.concat(terms.stream(), numberVariants(terms).stream()).toList();
    }

    /**
     * El analizador guarda «2024-530» como '2024' y '-530': el número suelto solo coincide como '-N'. Da también los
     * grupos de los compuestos («2024/530», «530/24»).
     */
    static List<String> numberVariants(List<String> terms) {
        List<String> variants = new ArrayList<>();
        tokens(terms).forEach(token -> {
            Matcher compound = COMPOUND.matcher(token);
            if (compound.matches()) {
                Stream.of(compound.group(1), compound.group(2)).filter(SearchSql::isCaseNumber).forEach(n -> {
                    variants.add(n);
                    variants.add("-" + n);
                });
            } else if (isCaseNumber(token)) {
                variants.add("-" + token);
            }
        });
        return variants.stream().distinct().toList();
    }

    private static List<String> caseNumbers(List<String> terms) {
        return numberVariants(terms).stream().map(v -> v.replaceFirst("^-", "")).distinct().toList();
    }

    private static Stream<String> tokens(List<String> terms) {
        return terms.stream().flatMap(t -> Arrays.stream(t.trim().split("\\s+")))
                .map(token -> EDGE_PUNCTUATION.matcher(token).replaceAll(""));
    }

    /** Tres cifras o más y que no sea un año (1900 a 2099). */
    private static boolean isCaseNumber(String s) {
        return NUMBER.matcher(s).matches() && !YEAR.matcher(s).matches();
    }

    /** Filtro opcional por un proyecto: incluye lo que no tiene proyecto (D-053). */
    static String inProject(String projectColumn) {
        return "(CAST(:project AS uuid) IS NULL OR " + projectColumn + " = :project OR " + projectColumn
                + " IS NULL)";
    }

    /**
     * Filtro opcional por un expediente (:caseId): su ficha y los documentos que se le han añadido. La ficha tiene que
     * ser de la empresa, de un proyecto visible y no estar retirada; si no, no sale nada del expediente.
     */
    static String inCaseFile(String alias) {
        return "(CAST(:caseId AS uuid) IS NULL OR (" + alias + ".id = :caseId AND " + alias + ".case_document) OR"
                + " (EXISTS (SELECT 1 FROM document k WHERE k.id = :caseId AND k.case_document AND "
                + Sql.inScope("k.company_id") + " AND " + Sql.inVisibleProjects("k.project_id") + " AND "
                + Sql.notWithdrawn("k.withdrawn_at") + ") AND EXISTS (SELECT 1 FROM case_file_entry e"
                + " WHERE e.case_document_id = :caseId AND e.document_id = " + alias + ".id)))";
    }

    /** Las fichas de conocimiento no son del expediente: con un :caseId no salen. */
    static final String NO_CASE_FILE = "CAST(:caseId AS uuid) IS NULL";

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
