package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.AdminChatKeyRepository;
import com.e2d.odin.domain.identity.AdminChatKey;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryAdminChatKeyRepository implements AdminChatKeyRepository {

    private final Map<UUID, AdminChatKey> keys = new LinkedHashMap<>();

    @Override
    public void save(AdminChatKey key) {
        keys.put(key.id(), key);
    }

    @Override
    public Optional<AdminChatKey> findById(UUID id) {
        return Optional.ofNullable(keys.get(id));
    }

    @Override
    public Optional<AdminChatKey> findByPrefix(String prefix) {
        return keys.values().stream().filter(k -> k.prefix().equals(prefix)).findFirst();
    }

    @Override
    public List<AdminChatKey> listActiveOf(UserId userId, Instant now) {
        return keys.values().stream().filter(k -> k.userId().equals(userId) && k.activeAt(now)).toList();
    }
}
