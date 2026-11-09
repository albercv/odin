package com.e2d.odin.domain.identity;

import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;

public record AccessKey(AccessKeyId id, UserId userId, String label, String prefix, String secretHash,
        Instant createdAt, Optional<Instant> revokedAt) {

    public AccessKey revoked(Instant at) {
        return new AccessKey(id, userId, label, prefix, secretHash, createdAt, Optional.of(at));
    }
}
