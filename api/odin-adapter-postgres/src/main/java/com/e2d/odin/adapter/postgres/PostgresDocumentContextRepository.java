package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.DocumentContextRepository;
import com.e2d.odin.domain.citation.LinkRelation;
import com.e2d.odin.domain.context.CorrectionDirection;
import com.e2d.odin.domain.context.DocumentContext;
import com.e2d.odin.domain.context.DocumentCorrection;
import com.e2d.odin.domain.context.DocumentVersion;
import com.e2d.odin.domain.context.PageText;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

/**
 * Cadena de versiones y vínculos de corrección de documentos ya visibles. Cada salto lleva el filtro explícito de
 * empresa, proyectos visibles y documentos listables, además de la seguridad por filas: lo que no se ve no sale con su
 * id ni su título.
 */
public class PostgresDocumentContextRepository implements DocumentContextRepository {

    private static final int HEADING_CHARS = 300;
    private static final String IDS = "CAST(string_to_array(:ids, ',') AS uuid[])";
    private static final String COMPONENTS = """
            WITH RECURSIVE comp(seed, id, path) AS (
                SELECT d.id, d.id, ARRAY[d.id] FROM document d
                WHERE d.id = ANY (%s) AND %s AND %s AND %s
                UNION ALL
                SELECT c.seed, n.id, c.path || n.id FROM comp c JOIN document cur ON cur.id = c.id
                JOIN document n ON n.company_id = cur.company_id AND (n.id = cur.superseded_by OR n.superseded_by = cur.id)
                    AND %s AND %s AND %s
                WHERE NOT n.id = ANY (c.path) AND cardinality(c.path) < %d
            )
            SELECT DISTINCT c.seed, n.id, n.title, n.superseded_by FROM comp c JOIN document n ON n.id = c.id""";
    private static final String HEADINGS = """
            SELECT DISTINCT ON (p.document_id) p.document_id, p.number, left(p.text, 400) AS head
            FROM document_page p WHERE p.document_id = ANY (%s) AND %s ORDER BY p.document_id, p.number""";
    private static final String LINKS = """
            SELECT l.source_document_id, l.target_document_id, l.relation, l.origin, l.status, l.evidence_page, l.evidence_quote,
                   s.title AS source_title, t.title AS target_title, t.superseded_by AS target_superseded_by
            FROM document_link l
            LEFT JOIN document s ON s.id = l.source_document_id AND %s AND %s AND %s
            LEFT JOIN document t ON t.id = l.target_document_id AND %s AND %s AND %s
            WHERE %s AND l.status <> 'DISMISSED' AND (l.source_document_id = ANY (%s) OR l.target_document_id = ANY (%s))
              AND s.title IS NOT NULL AND (l.target_document_id IS NULL OR t.title IS NOT NULL)
            ORDER BY l.relation, l.cited_identifier, l.id""";
    private static final String MATCHES = """
            WITH q AS (SELECT %s AS q)
            SELECT DISTINCT ON (r.document_id) r.document_id, r.number, r.text,
                   ts_headline('odin_spanish', r.text, q.q, 'StartSel="", StopSel="", MaxFragments=1') AS headline
            FROM (SELECT p.document_id, p.number, p.text, %s + ts_rank_cd(p.tsv, q.q)::float8 AS rank
                  FROM document_page p JOIN document d ON d.id = p.document_id, q
                  WHERE p.document_id = ANY (%s) AND %s AND %s AND %s AND (p.tsv @@ q.q OR %s)) r, q
            ORDER BY r.document_id, r.rank DESC, r.number""";

    private record LinkRow(UUID sourceId, UUID targetId, LinkRelation relation, String origin, String status, int page,
            String quote,
            String sourceTitle, String targetTitle, UUID targetSupersededBy) {

        /** Lo puso un administrador o lo confirmó una persona: solo eso cuenta como confirmado. */
        boolean confirmed() {
            return "ADMIN".equals(origin) || "CONFIRMED".equals(status);
        }

        /** Un REPLACES detectado y sin confirmar que no es una sustitución marcada: falta un eslabón. */
        boolean unconfirmedChange() {
            return targetId != null && !confirmed() && !markedSupersession() && relation == LinkRelation.REPLACES;
        }

        boolean touches(Set<UUID> chain) {
            return chain.contains(sourceId) || chain.contains(targetId);
        }

        boolean markedSupersession() {
            return relation == LinkRelation.REPLACES && sourceId.equals(targetSupersededBy);
        }
    }

    private final ScopedTransactions tx;

    public PostgresDocumentContextRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public Map<DocumentId, DocumentContext> contexts(CompanyScope scope, ProjectVisibility visibility,
            Collection<DocumentId> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return tx.inScope(scope, visibility, jdbc -> {
            Map<UUID, Set<VersionChain.Node>> components = components(jdbc, scope, visibility, ids);
            Set<UUID> all = new HashSet<>();
            components.values().forEach(nodes -> nodes.forEach(n -> all.add(n.id())));
            Map<UUID, PageText> headings = headings(jdbc, scope, all);
            List<LinkRow> links = links(jdbc, scope, visibility, all);
            Map<DocumentId, DocumentContext> result = new LinkedHashMap<>();
            components.forEach((seed, nodes) -> {
                VersionChain chain = VersionChain.of(nodes);
                result.put(new DocumentId(seed), contextOf(seed, chain, headings, links,
                        hidden(jdbc, seed, nodes.stream().map(VersionChain.Node::id).toList())));
            });
            return result;
        });
    }

    @Override
    public Map<DocumentId, List<DocumentCorrection>> corrections(CompanyScope scope, ProjectVisibility visibility,
            Collection<DocumentId> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return tx.inScope(scope, visibility, jdbc -> {
            Set<UUID> seeds = ids.stream().map(DocumentId::value).collect(Collectors.toSet());
            List<LinkRow> links = links(jdbc, scope, visibility, seeds);
            Map<DocumentId, List<DocumentCorrection>> result = new HashMap<>();
            seeds.forEach(seed -> {
                List<DocumentCorrection> all = new java.util.ArrayList<>(correctionsOf(seed, links));
                all.addAll(hidden(jdbc, seed, List.of(seed)).corrections());
                result.put(new DocumentId(seed), all);
            });
            return result;
        });
    }

    @Override
    public Map<DocumentId, PageText> bestMatches(CompanyScope scope, ProjectVisibility visibility, List<String> terms,
            Collection<DocumentId> ids) {
        List<String> usable = SearchSql.usableTerms(terms);
        if (ids.isEmpty() || usable.isEmpty()) {
            return Map.of();
        }
        List<String> queryTerms = SearchSql.withNumberVariants(usable);
        List<String> exact = SearchSql.exactTokens(usable);
        String sql = MATCHES.formatted(SearchSql.tsquery(queryTerms.size()), SearchSql.boost("p.text", exact.size()),
                IDS, Sql.inScope("p.company_id"), Sql.inVisibleProjects("d.project_id"), Sql.listable("d"),
                SearchSql.anyMatch("p.text", exact.size()));
        return tx.inScope(scope, visibility, jdbc -> {
            StatementSpec spec = Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility).param("ids", join(ids));
            Map<DocumentId, PageText> result = new HashMap<>();
            SearchSql.bindTerms(spec, queryTerms, exact).query((rs, n) -> {
                result.put(new DocumentId(Sql.uuid(rs, "document_id")), new PageText(rs.getInt("number"),
                        SearchSql.excerpt(rs.getString("headline"), rs.getString("text"), usable)));
                return n;
            }).list();
            return result;
        });
    }

    private static String join(Collection<DocumentId> ids) {
        return ids.stream().map(i -> i.value().toString()).collect(Collectors.joining(","));
    }

    private Map<UUID, Set<VersionChain.Node>> components(JdbcClient jdbc, CompanyScope scope,
            ProjectVisibility visibility, Collection<DocumentId> ids) {
        String sql = COMPONENTS.formatted(IDS, Sql.inScope("d.company_id"), Sql.inVisibleProjects("d.project_id"),
                Sql.visibleDocument("d"), Sql.inScope("n.company_id"), Sql.inVisibleProjects("n.project_id"),
                Sql.listable("n"), com.e2d.odin.domain.document.Document.MAX_SUPERSESSION_CHAIN);
        Map<UUID, Set<VersionChain.Node>> result = new HashMap<>();
        Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility).param("ids", join(ids)).query((rs, n) -> {
            result.computeIfAbsent(Sql.uuid(rs, "seed"), k -> new HashSet<>()).add(new VersionChain.Node(
                    Sql.uuid(rs, "id"), rs.getString("title"), Sql.uuid(rs, "superseded_by")));
            return n;
        }).list();
        return result;
    }

    private Map<UUID, PageText> headings(JdbcClient jdbc, CompanyScope scope, Set<UUID> ids) {
        String sql = HEADINGS.formatted(IDS, Sql.inScope("p.company_id"));
        Map<UUID, PageText> result = new HashMap<>();
        Sql.scoped(jdbc.sql(sql), scope).param("ids", joinUuids(ids)).query((rs, n) -> {
            result.put(Sql.uuid(rs, "document_id"), new PageText(rs.getInt("number"), heading(rs.getString("head"))));
            return n;
        }).list();
        return result;
    }

    private static String heading(String text) {
        if (text.length() <= HEADING_CHARS) {
            return text.strip();
        }
        String cut = text.substring(0, HEADING_CHARS);
        int lastBreak = Math.max(cut.lastIndexOf('\n'), cut.lastIndexOf(' '));
        return (lastBreak > HEADING_CHARS / 2 ? cut.substring(0, lastBreak) : cut).strip();
    }

    private static String joinUuids(Set<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    private List<LinkRow> links(JdbcClient jdbc, CompanyScope scope, ProjectVisibility visibility, Set<UUID> ids) {
        String sql = LINKS.formatted(Sql.inScope("s.company_id"), Sql.inVisibleProjects("s.project_id"),
                Sql.listable("s"), Sql.inScope("t.company_id"), Sql.inVisibleProjects("t.project_id"),
                Sql.listable("t"), Sql.inScope("l.company_id"), IDS, IDS);
        return Sql.visible(Sql.scoped(jdbc.sql(sql), scope), visibility).param("ids", joinUuids(ids))
                .query(PostgresDocumentContextRepository::linkRow).list();
    }

    private static LinkRow linkRow(ResultSet rs, int n) throws SQLException {
        return new LinkRow(Sql.uuid(rs, "source_document_id"), Sql.uuid(rs, "target_document_id"),
                LinkRelation.valueOf(rs.getString("relation")), rs.getString("origin"), rs.getString("status"),
                rs.getInt("evidence_page"),
                rs.getString("evidence_quote"), rs.getString("source_title"), rs.getString("target_title"),
                Sql.uuid(rs, "target_superseded_by"));
    }

    /** Solo recuentos: la función de la base no da ids, títulos ni frases de lo que no se ve. */
    private record Hidden(List<DocumentCorrection> corrections, boolean chain) {
    }

    private Hidden hidden(JdbcClient jdbc, UUID seed, Collection<UUID> chain) {
        List<DocumentCorrection> corrections = new java.util.ArrayList<>();
        boolean[] inChain = {false};
        jdbc.sql("SELECT kind, relation, hidden_count FROM document_hidden_context(:seed, "
                + "CAST(string_to_array(:chain, ',') AS uuid[]))").param("seed", seed)
                .param("chain", chain.stream().map(UUID::toString).collect(Collectors.joining(",")))
                .query((rs, n) -> {
                    if ("CHAIN".equals(rs.getString("kind"))) {
                        inChain[0] = rs.getInt("hidden_count") > 0;
                    } else {
                        CorrectionDirection direction = CorrectionDirection.valueOf(rs.getString("kind"));
                        for (int i = 0; i < rs.getInt("hidden_count"); i++) {
                            corrections.add(DocumentCorrection.hiddenOf(direction,
                                    LinkRelation.valueOf(rs.getString("relation"))));
                        }
                    }
                    return n;
                }).list();
        return new Hidden(corrections, inChain[0]);
    }

    private static DocumentContext contextOf(UUID seed, VersionChain chain, Map<UUID, PageText> headings,
            List<LinkRow> links, Hidden hidden) {
        List<DocumentCorrection> corrections = new java.util.ArrayList<>(correctionsOf(seed, links));
        corrections.addAll(hidden.corrections());
        Set<UUID> inChain = chain.ordered().stream().map(VersionChain.Node::id).collect(Collectors.toSet());
        boolean pending = links.stream().anyMatch(l -> l.targetId() == null && inChain.contains(l.sourceId()));
        boolean incomplete = chain.incomplete() || pending || hidden.chain() || !hidden.corrections().isEmpty()
                || links.stream().anyMatch(l -> l.unconfirmedChange() && l.touches(inChain));
        return new DocumentContext(versionsOf(chain, headings, links), corrections, incomplete);
    }

    private static List<DocumentVersion> versionsOf(VersionChain chain, Map<UUID, PageText> headings,
            List<LinkRow> links) {
        if (chain.ordered().size() < 2) {
            return List.of();
        }
        return chain.ordered().stream().map(node -> new DocumentVersion(new DocumentId(node.id()), node.title(),
                node.supersededBy() == null, supersessionLink(node.id(), links),
                Optional.ofNullable(headings.get(node.id())), Optional.empty())).toList();
    }

    private static Optional<PageText> supersessionLink(UUID version, List<LinkRow> links) {
        return links.stream().filter(l -> l.sourceId().equals(version) && l.markedSupersession()).findFirst()
                .map(l -> new PageText(l.page(), l.quote()));
    }

    private static List<DocumentCorrection> correctionsOf(UUID document, List<LinkRow> links) {
        return links.stream().filter(l -> !l.markedSupersession()).flatMap(l -> {
            if (l.sourceId().equals(document) && l.targetId() != null) {
                return java.util.stream.Stream.of(correction(CorrectionDirection.CORRECTS, l, l.targetId(),
                        l.targetTitle()));
            }
            if (document.equals(l.targetId())) {
                return java.util.stream.Stream.of(correction(CorrectionDirection.CORRECTED_BY, l, l.sourceId(),
                        l.sourceTitle()));
            }
            return java.util.stream.Stream.empty();
        }).toList();
    }

    private static DocumentCorrection correction(CorrectionDirection direction, LinkRow link, UUID otherId,
            String otherTitle) {
        return new DocumentCorrection(direction, link.relation(),
                Optional.of(new DocumentRef(new DocumentId(otherId), otherTitle)), false,
                Optional.of(new PageText(link.page(), link.quote())), Optional.of(link.confirmed()));
    }
}
