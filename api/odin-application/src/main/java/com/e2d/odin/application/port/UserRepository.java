package com.e2d.odin.application.port;

import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    void save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(String email);

    /** Las personas que existen entre las pedidas, en una sola consulta. */
    List<User> findAllById(Collection<UserId> ids);

    Optional<User> findGroupAdmin();

    /** Si el código ya lo tiene una persona, también borrada: un código nunca se reutiliza. */
    boolean personCodeExists(String personCode);

    void delete(UserId id, Instant at);
}
