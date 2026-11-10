package com.e2d.odin.application.fake;

import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.Set;

/** Guarda personas con su pertenencia en los dobles en memoria, como las construiría el alta de usuarios. */
public final class People {

    private People() {
    }

    /** Persona que pertenece a una empresa con un solo papel; {@code active} es el de la pertenencia. */
    public static User member(InMemoryUserRepository users, InMemoryMembershipRepository memberships, UserId id,
            String name, Optional<String> email, Role role, CompanyId company, boolean active,
            Optional<String> passwordHash) {
        User user = new User(id, name, email, false, true, passwordHash);
        users.save(user);
        memberships.save(new Membership(id, company, Set.of(role), active));
        return user;
    }

    public static User groupAdmin(InMemoryUserRepository users, UserId id, String name, Optional<String> email,
            boolean active, Optional<String> passwordHash) {
        User user = new User(id, name, email, true, active, passwordHash);
        users.save(user);
        return user;
    }
}
