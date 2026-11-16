package com.e2d.odin.application.port;

import com.e2d.odin.application.port.AuditContext.AuditRequest;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminSessions {

    String open(UserId userId, AuditRequest client);

    default String open(UserId userId) {
        return open(userId, AuditContext.SYSTEM);
    }

    /** Renueva la inactividad. Una sesión caducada se borra y lanza {@code SessionExpiredException}. */
    Optional<UserId> resolve(String token);

    /** La sesión del token, sin renovarla. */
    Optional<AdminSession> find(String token);

    List<AdminSession> ofUser(UserId userId);

    /** {@code false} si no existe o es de otra persona. */
    boolean closeOwn(UserId userId, UUID id);

    void close(String token);

    int closeAllOf(UserId userId);
}
