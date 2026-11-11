package com.e2d.odin.adapter.postgres;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

final class Sql {

    private Sql() {
    }

    /** Filtro de empresa en el propio SQL: segunda barrera si la conexión llegara a saltarse la RLS. */
    static String inScope(String companyColumn) {
        return "(:allCompanies OR " + companyColumn + " = ANY (CAST(string_to_array(:companyIds, ',') AS uuid[])))";
    }

    static StatementSpec scoped(StatementSpec spec, CompanyScope scope) {
        return spec.param("allCompanies", scope.allCompanies()).param("companyIds", companyIds(scope));
    }

    static String companyIds(CompanyScope scope) {
        return scope.companies().stream().map(CompanyId::value).map(Object::toString)
                .collect(Collectors.joining(","));
    }

    static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    static OffsetDateTime ts(Optional<Instant> instant) {
        return instant.map(Sql::ts).orElse(null);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static Optional<Instant> optionalInstant(ResultSet rs, String column) throws SQLException {
        return Optional.ofNullable(instant(rs, column));
    }

    static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }
}
