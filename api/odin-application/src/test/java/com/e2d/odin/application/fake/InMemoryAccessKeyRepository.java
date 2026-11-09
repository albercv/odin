package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.UserId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryAccessKeyRepository implements AccessKeyRepository {

    private final Map<AccessKeyId, AccessKey> keys = new LinkedHashMap<>();

    @Override
    public void save(AccessKey k) {
        keys.put(k.id(), k);
    }

    @Override
    public Optional<AccessKey> findById(AccessKeyId id) {
        return Optional.ofNullable(keys.get(id));
    }

    @Override
    public Optional<AccessKey> findByPrefix(String prefix) {
        return keys.values().stream().filter(k -> k.prefix().equals(prefix)).findFirst();
    }

    @Override
    public List<AccessKey> listByUser(UserId userId) {
        return keys.values().stream().filter(k -> k.userId().equals(userId)).toList();
    }
}
