package com.e2d.odin.domain.identity;

import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** {@code id} es lo que ve su dueño en la lista; la huella del token nunca sale del servidor. */
public record AdminSession(UUID id, String tokenHash, UserId userId, Instant createdAt, Instant lastSeenAt,
        Optional<String> userAgent, Optional<String> ip, Instant reauthenticatedAt) {

    public AdminSession(String tokenHash, UserId userId, Instant createdAt, Instant lastSeenAt) {
        this(UUID.randomUUID(), tokenHash, userId, createdAt, lastSeenAt, Optional.empty(), Optional.empty(),
                createdAt);
    }

    public AdminSession seenAt(Instant at) {
        return new AdminSession(id, tokenHash, userId, createdAt, at, userAgent, ip, reauthenticatedAt);
    }
}
