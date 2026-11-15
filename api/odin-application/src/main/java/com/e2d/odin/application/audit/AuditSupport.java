package com.e2d.odin.application.audit;

import com.e2d.odin.application.port.AuditContext.AuditRequest;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.InstallationToken;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.shared.ForbiddenException;
import java.util.Collection;

/** Piezas comunes de las entradas de administración. */
public final class AuditSupport {

    private AuditSupport() {
    }

    public static java.util.List<String> sorted(Collection<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).sorted().toList();
    }

    /** IP y navegador van solo en las entradas de acceso al panel. */
    public static AuditEntry withClient(AuditEntry entry, AuditRequest request) {
        AuditEntry result = entry;
        if (request.ipAddress().isPresent()) {
            result = result.with("ip", request.ipAddress().get());
        }
        if (request.userAgent().isPresent()) {
            result = result.with("userAgent", request.userAgent().get());
        }
        return result;
    }

    /** Comprueba el código de instalación; si es malo deja la entrada negada (fuera de la transacción) y lo propaga. */
    public static void requireInstallationToken(InstallationSettings settings, String token, AuditLog audit,
            AuditAction action) {
        try {
            InstallationToken.require(settings, token);
        } catch (ForbiddenException e) {
            audit.recordDenied(AuditEntry.byInstaller(action).denied());
            throw e;
        }
    }
}
