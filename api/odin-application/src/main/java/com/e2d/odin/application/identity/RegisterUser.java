package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;
import java.util.Set;

public class RegisterUser {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final PasswordHasher passwords;
    private final IdGenerator ids;

    public RegisterUser(UserRepository users, MembershipRepository memberships, OrganizationRepository organizations,
            PasswordHasher passwords, IdGenerator ids) {
        this.users = users;
        this.memberships = memberships;
        this.organizations = organizations;
        this.passwords = passwords;
        this.ids = ids;
    }

    public UserSummary execute(Actor actor, CompanyId companyId, NewUser newUser) {
        AccessPolicy.requireAdminOf(actor, companyId);
        organizations.findCompany(companyId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        requireAllowedRole(actor, newUser.role());
        boolean admin = newUser.role() != Role.WORKER;
        Optional<String> email = newUser.email().filter(e -> !e.isBlank()).map(Validation::normalizeEmail);
        if (admin && email.isEmpty()) {
            throw new ValidationException("El administrador necesita correo");
        }
        if (!admin && newUser.password().filter(p -> !p.isBlank()).isPresent()) {
            throw new ValidationException(UserAdministration.WORKER_HAS_NO_PASSWORD);
        }
        email.ifPresent(e -> UserAdministration.requireEmailFree(users, e, Optional.empty()));
        Optional<String> passwordHash = admin
                ? Optional.of(passwords.hash(Validation.requirePassword(newUser.password().orElse(null))))
                : Optional.empty();
        User user = new User(new UserId(ids.newId()), Validation.requireText(newUser.displayName(), "el nombre"),
                email, false, true, passwordHash);
        Membership membership = new Membership(user.id(), companyId, Set.of(newUser.role()), true);
        users.save(user);
        memberships.save(membership);
        return new UserSummary(user, Optional.of(membership));
    }

    private static void requireAllowedRole(Actor actor, Role role) {
        if (role == Role.GROUP_ADMIN) {
            throw new ValidationException("No se puede crear otro administrador del grupo");
        }
        if (role == Role.COMPANY_ADMIN && !actor.isGroupAdmin()) {
            throw new ForbiddenException("Solo el administrador del grupo crea administradores de empresa");
        }
    }
}
