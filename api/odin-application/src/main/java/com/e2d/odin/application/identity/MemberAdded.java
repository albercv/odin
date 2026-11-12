package com.e2d.odin.application.identity;

import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.organization.Company;

/** {@code keptPassword}: era una persona que ya existía con contraseña y pasa a administrar; se queda con ella. */
public record MemberAdded(User user, Membership membership, Company company, boolean alreadyExisted,
        boolean keptPassword) {
}
