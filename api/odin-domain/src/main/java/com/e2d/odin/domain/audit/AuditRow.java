package com.e2d.odin.domain.audit;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;

/** Una entrada tal como se lee. {@code actorName} sale de {@code audit_actor}; nunca hay correo. */
public record AuditRow(long id, Instant occurredAt, Optional<CompanyId> company, Optional<ProjectId> project,
        AuditActorKind actorKind, Optional<UserId> actorUserId, Optional<String> actorName,
        Optional<String> actorRole, AuditChannel channel, Optional<String> via, String action,
        Optional<String> targetKind, Optional<String> targetId, Optional<String> targetLabel, AuditOutcome outcome,
        String detailsJson) {

    /** Una persona sin fila en {@code audit_actor} fue suprimida; sin id, no se llegó a identificar. */
    public Optional<String> actorLabel() {
        if (actorKind != AuditActorKind.PERSON) {
            return Optional.empty();
        }
        return Optional.of(actorName.orElse(actorUserId.isPresent() ? "Persona suprimida" : "Usuario desconocido"));
    }
}
