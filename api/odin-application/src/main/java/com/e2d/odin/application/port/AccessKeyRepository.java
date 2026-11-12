package com.e2d.odin.application.port;

import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Optional;

public interface AccessKeyRepository {

    void save(AccessKey key);

    Optional<AccessKey> findById(AccessKeyId id);

    Optional<AccessKey> findByPrefix(String prefix);

    /** Las claves de una pertenencia: de esa persona y solo de esa empresa. */
    List<AccessKey> listByMembership(UserId userId, CompanyId companyId);
}
