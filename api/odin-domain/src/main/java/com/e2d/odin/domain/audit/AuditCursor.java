package com.e2d.odin.domain.audit;

import com.e2d.odin.domain.shared.ValidationException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** Posición opaca en la lista: la última fila vista, por fecha e id. */
public record AuditCursor(Instant occurredAt, long id) {

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((occurredAt + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    public static AuditCursor decode(String cursor) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
            return new AuditCursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            throw new ValidationException("El cursor no es válido");
        }
    }
}
