package com.e2d.odin.application.port;

import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Optional;

public interface AccessKeyRepository {

    void save(AccessKey key);

    Optional<AccessKey> findById(AccessKeyId id);

    Optional<AccessKey> findByPrefix(String prefix);

    List<AccessKey> listByUser(UserId userId);
}
