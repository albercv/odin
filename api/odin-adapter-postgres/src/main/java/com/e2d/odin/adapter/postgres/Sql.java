package com.e2d.odin.adapter.postgres;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

final class Sql {

    private Sql() {
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
