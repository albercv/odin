package com.e2d.odin.application.audit;

import com.e2d.odin.application.port.AuditReader;
import com.e2d.odin.application.port.AuditSettingsRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.audit.ChainHead;
import com.e2d.odin.domain.identity.Actor;
import java.util.Optional;
import java.util.OptionalLong;

/** Estado de la cadena y su cabeza (el sello). No revela filas, así que lo puede pedir cualquier administrador. */
public class GetAuditIntegrity {

    public record Integrity(boolean logEnabled, boolean intact, OptionalLong firstBrokenId, Optional<ChainHead> head) {
    }

    private final AuditReader reader;
    private final AuditSettingsRepository settings;

    public GetAuditIntegrity(AuditReader reader, AuditSettingsRepository settings) {
        this.reader = reader;
        this.settings = settings;
    }

    public Integrity execute(Actor actor) {
        AccessPolicy.requireAdmin(actor);
        OptionalLong broken = reader.firstBroken();
        return new Integrity(settings.get().enabled(), broken.isEmpty(), broken, reader.head());
    }
}
