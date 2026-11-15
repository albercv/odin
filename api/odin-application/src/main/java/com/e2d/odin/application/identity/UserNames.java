package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UserId;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** Nombre y apellidos de varias personas con una sola consulta; quien ya no existe no sale en el mapa.
 * Precondición: solo ids de quien subió documentos que el llamante ya está autorizado a ver. */
public class UserNames {

    private final UserRepository users;

    public UserNames(UserRepository users) {
        this.users = users;
    }

    public Map<UserId, String> of(Collection<UserId> ids) {
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::id, User::displayName));
    }
}
