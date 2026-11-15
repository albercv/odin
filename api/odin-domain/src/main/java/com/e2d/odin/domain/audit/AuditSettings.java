package com.e2d.odin.domain.audit;

import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;

/** El interruptor del registro. Apagado y sin consulta por el chat hasta que un administrador del grupo lo decide. */
public record AuditSettings(boolean enabled, Optional<String> usagePolicyReference, boolean chatEnabled,
        Optional<UserId> updatedBy, Optional<Instant> updatedAt) {
}
