package com.e2d.odin.application.port;

import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;

public interface AdminSessionRepository {

    void save(AdminSession session);

    Optional<AdminSession> findByTokenHash(String tokenHash);

    void delete(String tokenHash);

    void deleteByUser(UserId userId);
}
