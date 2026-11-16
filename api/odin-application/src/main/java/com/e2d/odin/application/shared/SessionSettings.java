package com.e2d.odin.application.shared;

import com.e2d.odin.domain.identity.AdminSession;
import java.time.Duration;
import java.time.Instant;

public record SessionSettings(Duration idleTimeout, Duration absoluteTimeout) {

    public SessionSettings() {
        this(Duration.ofMinutes(30), Duration.ofHours(12));
    }

    /** El primero de los dos límites que alcance la sesión. */
    public Instant expiresAt(AdminSession session) {
        Instant idle = session.lastSeenAt().plus(idleTimeout);
        Instant absolute = session.createdAt().plus(absoluteTimeout);
        return idle.isBefore(absolute) ? idle : absolute;
    }
}
