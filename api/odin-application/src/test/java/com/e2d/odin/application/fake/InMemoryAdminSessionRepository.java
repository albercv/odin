package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.AdminSessionRepository;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.shared.UserId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryAdminSessionRepository implements AdminSessionRepository {

    private final Map<String, AdminSession> sessions = new HashMap<>();

    @Override
    public void save(AdminSession s) {
        sessions.put(s.tokenHash(), s);
    }

    @Override
    public Optional<AdminSession> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(sessions.get(tokenHash));
    }

    @Override
    public void delete(String tokenHash) {
        sessions.remove(tokenHash);
    }

    @Override
    public void deleteByUser(UserId userId) {
        sessions.values().removeIf(s -> s.userId().equals(userId));
    }
}
