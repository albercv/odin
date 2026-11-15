package com.e2d.odin.application.audit;

import com.e2d.odin.application.port.AuditSettingsRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.audit.AuditSettings;
import com.e2d.odin.domain.identity.Actor;

public class GetAuditSettings {

    private final AuditSettingsRepository settings;

    public GetAuditSettings(AuditSettingsRepository settings) {
        this.settings = settings;
    }

    public AuditSettings execute(Actor actor) {
        AccessPolicy.requireGroupAdmin(actor);
        return settings.get();
    }
}
