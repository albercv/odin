package com.e2d.odin.application.port;

import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;

public interface AdminSessions {

    String open(UserId userId);

    Optional<UserId> resolve(String token);

    void close(String token);

    void closeAllOf(UserId userId);
}
