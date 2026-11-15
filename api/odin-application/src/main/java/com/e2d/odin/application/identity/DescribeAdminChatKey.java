package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminChatKeyRepository;
import com.e2d.odin.application.port.AuditSettingsRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.audit.AuditSettings;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.AdminChatKey;
import java.time.Clock;
import java.util.Optional;

/** La clave vigente de quien pregunta (sin el token) y si el chat está disponible para emitir una. */
public class DescribeAdminChatKey {

    public record Description(boolean chatAvailable, Optional<AdminChatKey> key) {
    }

    private final AdminChatKeyRepository keys;
    private final AuditSettingsRepository settings;
    private final Clock clock;

    public DescribeAdminChatKey(AdminChatKeyRepository keys, AuditSettingsRepository settings, Clock clock) {
        this.keys = keys;
        this.settings = settings;
        this.clock = clock;
    }

    public Description execute(Actor actor) {
        AccessPolicy.requireAdmin(actor);
        AuditSettings current = settings.get();
        return new Description(current.enabled() && current.chatEnabled(),
                keys.listActiveOf(actor.userId(), clock.instant()).stream().findFirst());
    }
}
