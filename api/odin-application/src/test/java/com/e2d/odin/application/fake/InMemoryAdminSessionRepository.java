package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.AdminSessionRepository;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.shared.UserId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    public List<AdminSession> findByUser(UserId userId) {
        return sessions.values().stream().filter(s -> s.userId().equals(userId)).toList();
    }

    @Override
    public int deleteByIdAndUser(UUID id, UserId userId) {
        return sessions.values().removeIf(s -> s.id().equals(id) && s.userId().equals(userId)) ? 1 : 0;
    }

    @Override
    public int deleteByUser(UserId userId) {
        int before = sessions.size();
        sessions.values().removeIf(s -> s.userId().equals(userId));
        return before - sessions.size();
    }
}
