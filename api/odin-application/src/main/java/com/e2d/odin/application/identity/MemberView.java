package com.e2d.odin.application.identity;

import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.Set;

/**
 * Lo que una empresa ve de una persona: solo lo que toca a su pertenencia, nunca las demás empresas.
 * {@code hasPassword} dice si tiene contraseña (nunca la contraseña) y solo es verdad para el administrador del
 * grupo: a un administrador de empresa no le toca saber si una persona administra otra empresa.
 */
public record MemberView(UserId userId, String displayName, String firstName, Optional<String> lastName,
        String personCode, String memberCode, Optional<String> email, Set<Role> roles, boolean active,
        boolean hasPassword) {

    public static MemberView of(User user, Membership membership, String companyCode, Actor viewer) {
        return new MemberView(user.id(), user.displayName(), user.firstName(), user.lastName(), user.personCode(),
                user.personCode() + "-" + companyCode, user.email(), membership.roles(),
                user.active() && membership.active(), viewer.isGroupAdmin() && user.passwordHash().isPresent());
    }
}
