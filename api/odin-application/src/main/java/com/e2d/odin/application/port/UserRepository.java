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

    /** Las personas con la marca de administrador del grupo, activas o no, sin las borradas. */
    List<User> listGroupAdmins();

    /**
     * Como {@link #listGroupAdmins()}, pero bloquea esas filas hasta que acabe la transacción en curso: quien
     * comprueba que queda otro administrador del grupo activo no compite con otra comprobación a la vez.
     */
    List<User> lockGroupAdmins();

    Optional<User> findGroupAdminByEmail(String email);

    /** Si el código ya lo tiene una persona, también borrada: un código nunca se reutiliza. */
    boolean personCodeExists(String personCode);

    void delete(UserId id, Instant at);
}
