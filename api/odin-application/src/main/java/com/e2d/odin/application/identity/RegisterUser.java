package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;

public class RegisterUser {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordHasher passwords;
    private final IdGenerator ids;

    public RegisterUser(UserRepository users, OrganizationRepository organizations, PasswordHasher passwords,
            IdGenerator ids) {
        this.users = users;
        this.organizations = organizations;
        this.passwords = passwords;
        this.ids = ids;
    }

    public User execute(Actor actor, CompanyId companyId, NewUser newUser) {
        AccessPolicy.requireAdminOf(actor, companyId);
        organizations.findCompany(companyId).orElseThrow(() -> new NotFoundException("Empresa no encontrada"));
        requireAllowedRole(actor, newUser.role());
        boolean admin = newUser.role() != Role.WORKER;
        Optional<String> email = newUser.email().filter(e -> !e.isBlank()).map(Validation::normalizeEmail);
        if (admin && email.isEmpty()) {
            throw new ValidationException("El administrador necesita correo");
        }
        if (!admin && newUser.password().filter(p -> !p.isBlank()).isPresent()) {
            throw new ValidationException("Un trabajador no tiene contraseña: entra con su clave personal");
        }
        email.flatMap(users::findByEmail).ifPresent(existing -> {
            throw new ConflictException("Ya existe un usuario con ese correo");
        });
        Optional<String> passwordHash = admin
                ? Optional.of(passwords.hash(Validation.requirePassword(newUser.password().orElse(null))))
                : Optional.empty();
        User user = new User(new UserId(ids.newId()), Validation.requireText(newUser.displayName(), "el nombre"),
                email, newUser.role(), Optional.of(companyId), true, passwordHash);
        users.save(user);
        return user;
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
