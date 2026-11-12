package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.UsageStatistics;
import com.e2d.odin.application.usage.CardCounts;
import com.e2d.odin.application.usage.CompanyUsage;
import com.e2d.odin.application.usage.DocumentCounts;
import com.e2d.odin.application.usage.GapGroup;
import com.e2d.odin.application.usage.PersonUsage;
import com.e2d.odin.application.usage.QuestionCounts;
import com.e2d.odin.application.usage.TaggingCounts;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.knowledge.QuestionKind;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Collator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

/**
 * Recuentos de una empresa y mes. Cada consulta lleva su filtro de empresa y corre con la seguridad por filas de esa
 * única empresa, también para el administrador del grupo: las empresas nunca se mezclan. El mes es un rango de
 * instantes calculado con la zona de la instalación.
 */
public class PostgresUsageStatistics implements UsageStatistics {

    private static final String IN_RANGE = " AND %s >= :from AND %s < :to";
    private static final String QUESTIONS = """
            SELECT count(*) FILTER (WHERE kind = 'SEARCH') AS search,
                count(*) FILTER (WHERE kind = 'SIMILAR_CASES') AS similar_cases,
                count(*) FILTER (WHERE hit_count = 0) AS without_results
            FROM question_record WHERE %s""".formatted(Sql.inScope("company_id") + IN_RANGE.formatted("asked_at", "asked_at"));
    private static final String CARDS = """
            SELECT count(*) AS saved, count(*) FILTER (WHERE decision = 'APPROVE') AS approved,
                count(*) FILTER (WHERE decision = 'CORRECT') AS corrected,
                count(*) FILTER (WHERE status = 'REJECTED') AS rejected,
                count(*) FILTER (WHERE status = 'PENDING') AS pending
            FROM knowledge_card WHERE %s AND %s""".formatted(
            Sql.inScope("company_id") + IN_RANGE.formatted("created_at", "created_at"),
            Sql.inVisibleProjects("project_id"));
    private static final String DOCUMENTS = """
            SELECT count(*) AS uploaded, count(*) FILTER (WHERE status = 'READY') AS ready,
                count(*) FILTER (WHERE status = 'FAILED') AS failed,
                count(*) FILTER (WHERE status IN ('PENDING_READER', 'READER_PROCESSING')) AS waiting
            FROM document WHERE %s AND %s""".formatted(
            Sql.inScope("company_id") + IN_RANGE.formatted("uploaded_at", "uploaded_at"),
            Sql.inVisibleProjects("project_id"));
    private static final String HAS_TOPICS = """
            SELECT EXISTS (SELECT 1 FROM topic WHERE deleted_at IS NULL AND %s)""".formatted(Sql.inScope("company_id"));
    private static final String READY_DOCUMENTS = """
            SELECT count(*) FROM document d WHERE d.status = 'READY' AND %s AND %s""".formatted(
            Sql.inScope("d.company_id"), Sql.inVisibleProjects("d.project_id"));
    private static final String COMPANY_CODE = "SELECT code FROM company WHERE " + Sql.inScope("id");
    /** Las pertenencias vivas, más quien tuvo actividad en el mes aunque ya no pertenezca. */
    private static final String PEOPLE = """
            WITH q AS (SELECT author_id, count(*) AS n FROM question_record WHERE %s GROUP BY author_id),
            c AS (SELECT author_id, count(*) AS n FROM knowledge_card WHERE %s AND %s GROUP BY author_id),
            who AS (SELECT m.user_id AS id FROM membership m JOIN app_user a ON a.id = m.user_id
                    WHERE m.deleted_at IS NULL AND a.deleted_at IS NULL AND %s
                UNION SELECT author_id FROM q UNION SELECT author_id FROM c)
            SELECT u.id, u.first_name, u.last_name, u.person_code, u.deleted_at IS NOT NULL AS departed,
                coalesce(q.n, 0) AS questions, coalesce(c.n, 0) AS cards
            FROM who JOIN app_user u ON u.id = who.id
            LEFT JOIN q ON q.author_id = u.id LEFT JOIN c ON c.author_id = u.id""".formatted(
            Sql.inScope("company_id") + IN_RANGE.formatted("asked_at", "asked_at"),
            Sql.inScope("company_id") + IN_RANGE.formatted("created_at", "created_at"),
            Sql.inVisibleProjects("project_id"), Sql.inScope("m.company_id"));
    /** Sin autor en ninguna columna. El texto del grupo es el menor de los que lo forman. */
    private static final String GAPS = """
            SELECT min(text) AS text, kind, count(*) AS times, (max(asked_at) AT TIME ZONE :zone)::date AS last_day
            FROM question_record WHERE hit_count = 0 AND %s
            GROUP BY btrim(regexp_replace(lower(unaccent(text)), '\\s+', ' ', 'g')), kind
            ORDER BY max(asked_at) DESC, min(text) LIMIT :limit""".formatted(
            Sql.inScope("company_id") + IN_RANGE.formatted("asked_at", "asked_at"));

    private record Person(User user, boolean departed, int questions, int cards) {
        PersonUsage view(String companyCode) {
            return new PersonUsage(Optional.of(user.id()), user.displayName(),
                    Optional.of(user.personCode() + "-" + companyCode), questions, cards, false);
        }
    }

    private final ScopedTransactions tx;

    public PostgresUsageStatistics(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public CompanyUsage companyUsage(CompanyId companyId, YearMonth month, ZoneId zone) {
        return read(companyId, month, zone, (jdbc, scope) -> {
            int[] q = counts(spec(jdbc, QUESTIONS, scope, month, zone), "search", "similar_cases", "without_results");
            int[] c = counts(spec(jdbc, CARDS, scope, month, zone), "saved", "approved", "corrected", "rejected",
                    "pending");
            int[] d = counts(spec(jdbc, DOCUMENTS, scope, month, zone), "uploaded", "ready", "failed", "waiting");
            return new CompanyUsage(new QuestionCounts(q[0], q[1]), q[2], new CardCounts(c[0], c[1], c[2], c[3], c[4]),
                    new DocumentCounts(d[0], d[1], d[2], d[3]), tagging(jdbc, scope));
        });
    }

    @Override
    public List<PersonUsage> peopleUsage(CompanyId companyId, YearMonth month, ZoneId zone) {
        return read(companyId, month, zone, (jdbc, scope) -> {
            String code = Sql.scoped(jdbc.sql(COMPANY_CODE), scope).query(String.class).single();
            List<Person> people = spec(jdbc, PEOPLE, scope, month, zone).query((rs, n) -> person(rs)).list();
            return peopleView(people, code);
        });
    }

    @Override
    public List<GapGroup> gaps(CompanyId companyId, YearMonth month, ZoneId zone, int limit) {
        return read(companyId, month, zone, (jdbc, scope) -> spec(jdbc, GAPS, scope, month, zone)
                .param("limit", limit).query((rs, n) -> gap(rs)).list());
    }

    private <T> T read(CompanyId companyId, YearMonth month, ZoneId zone, Reader<T> reader) {
        CompanyScope scope = CompanyScope.of(companyId);
        return tx.inScope(scope, ProjectVisibility.all(), jdbc -> reader.read(jdbc, scope));
    }

    private interface Reader<T> {
        T read(JdbcClient jdbc, CompanyScope scope);
    }

    private static StatementSpec spec(JdbcClient jdbc, String sql, CompanyScope scope, YearMonth month, ZoneId zone) {
        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        return Sql.visible(Sql.scoped(jdbc.sql(sql), scope), ProjectVisibility.all()).param("from", Sql.ts(from))
                .param("to", Sql.ts(to)).param("zone", zone.getId());
    }

    private static int[] counts(StatementSpec spec, String... columns) {
        return spec.query((rs, n) -> {
            int[] values = new int[columns.length];
            for (int i = 0; i < columns.length; i++) {
                values[i] = rs.getInt(columns[i]);
            }
            return values;
        }).single();
    }

    private static Optional<TaggingCounts> tagging(JdbcClient jdbc, CompanyScope scope) {
        ProjectVisibility all = ProjectVisibility.all();
        if (!Sql.scoped(jdbc.sql(HAS_TOPICS), scope).query(Boolean.class).single()) {
            return Optional.empty();
        }
        int ready = Sql.visible(Sql.scoped(jdbc.sql(READY_DOCUMENTS), scope), all).query(Integer.class).single();
        int pending = Sql.visible(Sql.scoped(jdbc.sql("SELECT count(*) "
                + PostgresTaggingRepository.PENDING.formatted(Sql.inVisibleProjects("d.project_id"))), scope), all)
                .query(Integer.class).single();
        return Optional.of(new TaggingCounts(Math.max(ready - pending, 0), pending));
    }

    /** Por nombre, nunca por cantidad; el empate lo deshace el código de persona. Las bajas, sumadas al final. */
    private static List<PersonUsage> peopleView(List<Person> people, String companyCode) {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("es"));
        List<PersonUsage> live = people.stream().filter(p -> !p.departed())
                .sorted(Comparator.comparing((Person p) -> p.user().displayName(), collator)
                        .thenComparing(p -> p.user().personCode()))
                .map(p -> p.view(companyCode)).toList();
        List<Person> departed = people.stream().filter(Person::departed).toList();
        if (departed.isEmpty()) {
            return live;
        }
        PersonUsage row = PersonUsage.departed(departed.stream().mapToInt(Person::questions).sum(),
                departed.stream().mapToInt(Person::cards).sum());
        return java.util.stream.Stream.concat(live.stream(), java.util.stream.Stream.of(row)).toList();
    }

    private static Person person(ResultSet rs) throws SQLException {
        User user = new User(new UserId(Sql.uuid(rs, "id")), rs.getString("first_name"),
                Optional.ofNullable(rs.getString("last_name")), rs.getString("person_code"), Optional.empty(), false,
                true, Optional.empty());
        return new Person(user, rs.getBoolean("departed"), rs.getInt("questions"), rs.getInt("cards"));
    }

    private static GapGroup gap(ResultSet rs) throws SQLException {
        return new GapGroup(rs.getString("text"), QuestionKind.valueOf(rs.getString("kind")), rs.getInt("times"),
                rs.getObject("last_day", LocalDate.class));
    }
}
