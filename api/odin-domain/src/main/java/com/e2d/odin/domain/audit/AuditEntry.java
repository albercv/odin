package com.e2d.odin.domain.audit;

import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Un hecho a registrar. El canal y el id de petición no van aquí: los pone el adaptador desde el contexto de la
 * petición. {@code details} nunca lleva contenido de documentos, fichas, preguntas, contraseñas ni claves completas.
 */
public record AuditEntry(AuditAction action, AuditOutcome outcome, AuditActorKind actorKind,
        Optional<UserId> actorUserId, Optional<String> actorRole, Optional<String> actorName,
        Optional<CompanyId> company, Optional<ProjectId> project, Optional<String> via, Optional<Target> target,
        Map<String, Object> details) {

    public record Target(String kind, String id, String label) {
    }

    public AuditEntry {
        details = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static AuditEntry by(Actor actor, AuditAction action) {
        return new AuditEntry(action, AuditOutcome.DONE, AuditActorKind.PERSON, Optional.of(actor.userId()),
                Optional.of(actor.role().name()), Optional.of(actor.displayName()), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Map.of());
    }

    public static AuditEntry bySystem(AuditAction action) {
        return new AuditEntry(action, AuditOutcome.DONE, AuditActorKind.SYSTEM, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Map.of());
    }

    public static AuditEntry byUnknownPerson(AuditAction action) {
        return new AuditEntry(action, AuditOutcome.DONE, AuditActorKind.PERSON, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Map.of());
    }

    /** Quien actúa es el enlace de acceso, no una persona identificada: {@code via} es el enlace y su dueño va en los detalles. */
    public static AuditEntry byLink(java.util.UUID linkId, UserId owner, AuditAction action) {
        return new AuditEntry(action, AuditOutcome.DONE, AuditActorKind.LINK, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(linkId.toString()),
                Optional.empty(), Map.of("linkOwnerId", owner.value().toString()));
    }

    public static AuditEntry byInstaller(AuditAction action) {
        return new AuditEntry(action, AuditOutcome.DONE, AuditActorKind.INSTALLER, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Map.of());
    }

    public AuditEntry denied() {
        return new AuditEntry(action, AuditOutcome.DENIED, actorKind, actorUserId, actorRole, actorName, company,
                project, via, target, details);
    }

    public AuditEntry onCompany(CompanyId id) {
        return new AuditEntry(action, outcome, actorKind, actorUserId, actorRole, actorName, Optional.of(id),
                project, via, target, details);
    }

    public AuditEntry onProject(ProjectId id) {
        return new AuditEntry(action, outcome, actorKind, actorUserId, actorRole, actorName, company,
                Optional.of(id), via, target, details);
    }

    public AuditEntry on(String kind, String id, String label) {
        return new AuditEntry(action, outcome, actorKind, actorUserId, actorRole, actorName, company, project, via,
                Optional.of(new Target(kind, id, label)), details);
    }

    public AuditEntry with(String key, Object value) {
        Map<String, Object> merged = new LinkedHashMap<>(details);
        merged.put(key, value);
        return new AuditEntry(action, outcome, actorKind, actorUserId, actorRole, actorName, company, project, via,
                target, merged);
    }
}
