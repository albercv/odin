package com.e2d.odin.application.port;

import com.e2d.odin.domain.identity.AdminChatKey;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminChatKeyRepository {

    void save(AdminChatKey key);

    Optional<AdminChatKey> findById(UUID id);

    Optional<AdminChatKey> findByPrefix(String prefix);

    /** Las claves de esa persona que ni están revocadas ni han caducado. */
    List<AdminChatKey> listActiveOf(UserId userId, Instant now);
}
