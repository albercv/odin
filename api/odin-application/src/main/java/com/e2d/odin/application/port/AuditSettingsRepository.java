package com.e2d.odin.application.port;

import com.e2d.odin.domain.audit.AuditSettings;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;

public interface AuditSettingsRepository {

    AuditSettings get();

    void update(boolean enabled, Optional<String> usagePolicyReference, boolean chatEnabled, UserId by);
}
