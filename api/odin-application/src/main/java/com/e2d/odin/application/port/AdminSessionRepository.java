package com.e2d.odin.application.port;

import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminSessionRepository {

    void save(AdminSession session);

    Optional<AdminSession> findByTokenHash(String tokenHash);

    List<AdminSession> findByUser(UserId userId);

    void delete(String tokenHash);

    /** Cuántas filas borró: 0 si no era de esa persona o no existe. */
    int deleteByIdAndUser(UUID id, UserId userId);

    int deleteByUser(UserId userId);
}
