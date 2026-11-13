package com.e2d.odin.adapter.postgres;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.shared.DocumentId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** El SQL de las sustituciones, en una sola pieza: lo usan el repositorio de documentos y la búsqueda. */
final class SupersessionSql {

    /** Las columnas que añade {@link #joins}: el sustituto directo y el vigente del final de la cadena. */
    static final String COLUMNS = "sd.id AS direct_id, sd.title AS direct_title, sc.current_id, sc.current_title";

    private SupersessionSql() {
    }

    /**
     * LEFT JOIN del sustituto directo (alias sd) y del vigente del final de la cadena (alias sc) de la columna dada.
     * Cada salto es de la empresa del documento de partida (la columna dada), lleva el filtro explícito de empresa y de
     * proyectos del que lee, y va bajo la seguridad por filas. Si la cadena
     * se corta (un eslabón no se ve) o no termina (circular, con tope de saltos), el vigente es nulo.
     * Usa los parámetros de {@link Sql#scoped} y {@link Sql#visible}.
     */
    static String joins(String supersededByColumn, String companyColumn) {
        return """
                LEFT JOIN document sd ON sd.id = %s AND sd.company_id = %s AND %s AND %s AND %s
                LEFT JOIN LATERAL (
                    WITH RECURSIVE chain(id, title, superseded_by, company_id, hops) AS (
                        SELECT sd.id, sd.title, sd.superseded_by, sd.company_id, 1 WHERE sd.id IS NOT NULL
                        UNION ALL
                        SELECT n.id, n.title, n.superseded_by, n.company_id, c.hops + 1
                        FROM chain c JOIN document n ON n.id = c.superseded_by AND n.company_id = c.company_id AND %s AND %s AND %s
                        WHERE c.hops < %d
                    )
                    SELECT id AS current_id, title AS current_title FROM chain WHERE superseded_by IS NULL
                ) sc ON true""".formatted(supersededByColumn, companyColumn, Sql.inScope("sd.company_id"),
                Sql.inVisibleProjects("sd.project_id"), Sql.notWithdrawn("sd.withdrawn_at"),
                Sql.inScope("n.company_id"), Sql.inVisibleProjects("n.project_id"), Sql.notWithdrawn("n.withdrawn_at"),
                Document.MAX_SUPERSESSION_CHAIN);
    }

    static Optional<DocumentRef> direct(ResultSet rs) throws SQLException {
        return ref(rs, "direct_id", "direct_title");
    }

    static Optional<DocumentRef> current(ResultSet rs) throws SQLException {
        return ref(rs, "current_id", "current_title");
    }

    private static Optional<DocumentRef> ref(ResultSet rs, String idColumn, String titleColumn) throws SQLException {
        UUID id = Sql.uuid(rs, idColumn);
        return id == null ? Optional.empty() : Optional.of(new DocumentRef(new DocumentId(id), rs.getString(titleColumn)));
    }
}
