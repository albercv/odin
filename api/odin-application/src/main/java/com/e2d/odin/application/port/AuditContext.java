package com.e2d.odin.application.port;

import com.e2d.odin.domain.audit.AuditChannel;
import java.util.Optional;

/** Desde dónde y cómo llega la petición en curso, deducido en el servidor. Fuera de HTTP es el sistema. */
public interface AuditContext {

    AuditRequest SYSTEM = new AuditRequest(AuditChannel.SYSTEM, Optional.empty(), Optional.empty(), Optional.empty());

    AuditRequest current();

    record AuditRequest(AuditChannel channel, Optional<String> requestId, Optional<String> ipAddress,
            Optional<String> userAgent) {
    }
}
