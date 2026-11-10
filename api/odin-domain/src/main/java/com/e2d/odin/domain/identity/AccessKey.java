package com.e2d.odin.domain.identity;

import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;

/** Clave personal de una pertenencia: dice quién es la persona y en qué empresa actúa. */
public record AccessKey(AccessKeyId id, UserId userId, CompanyId companyId, String label, String prefix,
        String secretHash, Instant createdAt, Optional<Instant> revokedAt) {

    public AccessKey revoked(Instant at) {
        return new AccessKey(id, userId, companyId, label, prefix, secretHash, createdAt, Optional.of(at));
    }
}
