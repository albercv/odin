package com.e2d.odin.bootstrap;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Frena el arranque si el rol de la aplicación se saltaría la RLS (spec 7): superusuario, BYPASSRLS o dueño. */
final class DatabaseRoleGuard {

    private static final String ROLE = """
            SELECT current_user AS role, r.rolsuper, r.rolbypassrls, pg_has_role(t.tableowner, 'USAGE') AS owner
            FROM pg_roles r, pg_tables t
            WHERE r.rolname = current_user AND t.schemaname = current_schema() AND t.tablename = 'document'""";

    private DatabaseRoleGuard() {
    }

    static void check(DataSource dataSource) {
        JdbcClient.create(dataSource).sql(ROLE).query((rs, n) -> {
            List<String> problems = new ArrayList<>();
            if (rs.getBoolean("rolsuper")) {
                problems.add("es superusuario");
            }
            if (rs.getBoolean("rolbypassrls")) {
                problems.add("tiene BYPASSRLS");
            }
            if (rs.getBoolean("owner")) {
                problems.add("es dueño de la tabla document");
            }
            if (!problems.isEmpty()) {
                throw new IllegalStateException("La API no arranca: el rol de base de datos «" + rs.getString("role")
                        + "» " + String.join(", ", problems) + ", así que se saltaría el aislamiento entre empresas"
                        + " (RLS). Conéctala con el rol odin_app.");
            }
            return problems;
        }).single();
    }
}
