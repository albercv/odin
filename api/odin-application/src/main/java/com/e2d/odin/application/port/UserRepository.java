package com.e2d.odin.application.port;

import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Optional;

public interface UserRepository {

    void save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(String email);

    Optional<User> findGroupAdmin();

    void delete(UserId id, Instant at);
}
