package com.e2d.odin.domain.identity;

import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;

public record AdminSession(String tokenHash, UserId userId, Instant createdAt, Instant lastSeenAt) {

    public AdminSession seenAt(Instant at) {
        return new AdminSession(tokenHash, userId, createdAt, at);
    }
}
