package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.AuditSettingsRepository;
import com.e2d.odin.domain.audit.AuditSettings;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryAuditSettings implements AuditSettingsRepository {

    private final List<String> events;
    private AuditSettings current = new AuditSettings(false, Optional.empty(), false, Optional.empty(),
            Optional.empty());

    public InMemoryAuditSettings(List<String> events) {
        this.events = events;
    }

    public InMemoryAuditSettings() {
        this(new ArrayList<>());
    }

    @Override
    public AuditSettings get() {
        return current;
    }

    @Override
    public void update(boolean enabled, Optional<String> usagePolicyReference, boolean chatEnabled, UserId by) {
        events.add("update:" + enabled);
        current = new AuditSettings(enabled, usagePolicyReference, chatEnabled, Optional.of(by),
                Optional.of(Instant.EPOCH));
    }
}
