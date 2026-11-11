package com.e2d.odin.application.identity;

import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.Set;

/** Lo que una empresa ve de una persona: solo lo que toca a su pertenencia, nunca las demás empresas. */
public record MemberView(UserId userId, String displayName, String firstName, Optional<String> lastName,
        String personCode, String memberCode, Optional<String> email, Set<Role> roles, boolean active) {

    public static MemberView of(User user, Membership membership, String companyCode) {
        return new MemberView(user.id(), user.displayName(), user.firstName(), user.lastName(), user.personCode(),
                user.personCode() + "-" + companyCode, user.email(), membership.roles(),
                user.active() && membership.active());
    }
}
