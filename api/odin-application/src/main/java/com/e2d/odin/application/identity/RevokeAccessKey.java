package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.application.port.AccessLinkRepository;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.NotFoundException;
import java.time.Clock;

public class RevokeAccessKey {

    private final AccessKeyRepository keys;
    private final UserRepository users;
    private final AccessLinkRepository links;
    private final Clock clock;
    private final AuditLog audit;

    public RevokeAccessKey(AccessKeyRepository keys, UserRepository users, AccessLinkRepository links,
            Clock clock, AuditLog audit) {
        this.audit = audit;
        this.keys = keys;
        this.users = users;
        this.links = links;
        this.clock = clock;
    }

    public void execute(Actor actor, AccessKeyId id) {
        AccessKey key = keys.findById(id).orElseThrow(() -> new NotFoundException("Clave no encontrada"));
        users.findById(key.userId()).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        AccessPolicy.requireAdmin(actor);
        if (!actor.administers(key.companyId())) {
            throw new NotFoundException("Clave no encontrada");
        }
        if (key.revokedAt().isEmpty()) {
            keys.save(key.revoked(clock.instant()));
            audit.record(AuditEntry.by(actor, AuditAction.ACCESS_KEY_REVOKED).onCompany(key.companyId())
                    .on("ACCESS_KEY", id.value().toString(), null).with("prefix", key.prefix())
                    .with("workerId", key.userId().value().toString()));
        }
        links.deleteOf(key.companyId(), key.userId());
    }
}
