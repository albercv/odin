package com.e2d.odin.domain.audit;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Lo que el administrador puede filtrar al consultar el registro. {@code to} es exclusivo. */
public record AuditFilter(Optional<Instant> from, Optional<Instant> to, Optional<CompanyId> company,
        Optional<ProjectId> project, Optional<AuditAction> action, Optional<UserId> actor,
        Optional<AuditChannel> channel, Optional<AuditOutcome> outcome) {

    /** Los filtros puestos, para dejarlos en los detalles de la exportación. */
    public Map<String, Object> asDetails() {
        Map<String, Object> map = new LinkedHashMap<>();
        from.ifPresent(v -> map.put("from", v.toString()));
        to.ifPresent(v -> map.put("to", v.toString()));
        company.ifPresent(v -> map.put("companyId", v.value().toString()));
        project.ifPresent(v -> map.put("projectId", v.value().toString()));
        action.ifPresent(v -> map.put("action", v.name()));
        actor.ifPresent(v -> map.put("actorUserId", v.value().toString()));
        channel.ifPresent(v -> map.put("channel", v.name()));
        outcome.ifPresent(v -> map.put("outcome", v.name()));
        return map;
    }
}
