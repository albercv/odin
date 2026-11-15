package com.e2d.odin.domain.identity;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Credencial del administrador para leer el registro desde su chat. Sin empresa, es de ámbito de grupo. */
public record AdminChatKey(UUID id, UserId userId, Optional<CompanyId> companyId, String prefix, String tokenHash,
        Instant createdAt, Instant expiresAt, Optional<Instant> revokedAt) {

    public boolean activeAt(Instant now) {
        return revokedAt.isEmpty() && now.isBefore(expiresAt);
    }

    public AdminChatKey revoked(Instant at) {
        return new AdminChatKey(id, userId, companyId, prefix, tokenHash, createdAt, expiresAt, Optional.of(at));
    }
}
