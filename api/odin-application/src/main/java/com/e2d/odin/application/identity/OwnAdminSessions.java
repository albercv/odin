package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.shared.NotFoundException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Las sesiones abiertas del propio administrador: verlas, cerrarlas y saber cuándo caduca la actual. */
public class OwnAdminSessions {

    public record Listed(AdminSession session, boolean current) {
    }

    private final AdminSessions sessions;
    private final SessionSettings settings;
    private final AuditLog audit;

    public OwnAdminSessions(AdminSessions sessions, SessionSettings settings, AuditLog audit) {
        this.sessions = sessions;
        this.settings = settings;
        this.audit = audit;
    }

    public List<Listed> list(Actor actor, String currentToken) {
        AccessPolicy.requireAdmin(actor);
        Optional<UUID> current = sessions.find(currentToken).map(AdminSession::id);
        return sessions.ofUser(actor.userId()).stream()
                .sorted(Comparator.comparing(AdminSession::createdAt).reversed())
                .map(s -> new Listed(s, current.filter(s.id()::equals).isPresent())).toList();
    }

    public void close(Actor actor, UUID id) {
        AccessPolicy.requireAdmin(actor);
        if (!sessions.closeOwn(actor.userId(), id)) {
            throw new NotFoundException("Sesión no encontrada");
        }
        audit.record(AuditEntry.by(actor, AuditAction.SESSION_CLOSED).on("SESSION", id.toString(), null));
    }

    public record Expiry(Instant expiresAt, Instant absoluteExpiresAt) {
    }

    public Optional<Expiry> expiry(String token) {
        return sessions.find(token).map(s -> new Expiry(settings.expiresAt(s), settings.absoluteExpiresAt(s)));
    }
}
