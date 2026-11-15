package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminChatKeyRepository;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.AdminChatKey;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.time.Clock;
import java.util.UUID;

/** La revoca su dueño o cualquier administrador del grupo. Revocar dos veces no hace nada más. */
public class RevokeAdminChatKey {

    private final AdminChatKeyRepository keys;
    private final Clock clock;
    private final AuditLog audit;

    public RevokeAdminChatKey(AdminChatKeyRepository keys, Clock clock, AuditLog audit) {
        this.keys = keys;
        this.clock = clock;
        this.audit = audit;
    }

    public void execute(Actor actor, UUID keyId) {
        AccessPolicy.requireAdmin(actor);
        AdminChatKey key = keys.findById(keyId).orElseThrow(() -> new NotFoundException("Clave no encontrada"));
        if (!key.userId().equals(actor.userId()) && !actor.isGroupAdmin()) {
            throw new ForbiddenException("Solo su dueño o el administrador del grupo puede revocar esta clave");
        }
        if (key.revokedAt().isPresent()) {
            return;
        }
        keys.save(key.revoked(clock.instant()));
        audit.record(IssueAdminChatKey.entry(actor, AuditAction.ADMIN_CHAT_KEY_REVOKED, key)
                .with("ownerId", key.userId().value().toString()));
    }
}
