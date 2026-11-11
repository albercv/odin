package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class UserAdministration {

    static final String WORKER_HAS_NO_PASSWORD = "Un trabajador no tiene contraseña: entra con su clave personal";
    static final String ADMINISTRATOR_NEEDS_EMAIL = "El administrador necesita correo";

    private UserAdministration() {
    }

    /** Falla si otro usuario (distinto de {@code editing}, si lo hay) ya usa el correo. */
    static void requireEmailFree(UserRepository users, String email, Optional<UserId> editing) {
        users.findByEmail(email).filter(other -> editing.map(id -> !other.id().equals(id)).orElse(true))
                .ifPresent(other -> {
                    throw new ConflictException("Ya existe un usuario con ese correo");
                });
    }

    /** Empresas donde la persona tiene una pertenencia activa como administrador de empresa. */
    static Set<CompanyId> companiesAdministeredBy(MembershipRepository memberships, UserId userId) {
        return memberships.listByUser(userId).stream().filter(m -> m.active() && m.isCompanyAdmin())
                .map(Membership::companyId).collect(Collectors.toSet());
    }

    /** La pertenencia de la persona a esa empresa; si no existe, 404 (no distingue "otra empresa" de "nadie"). */
    static Membership requireMembership(MembershipRepository memberships, CompanyId companyId, UserId userId) {
        return memberships.find(userId, companyId)
                .orElseThrow(() -> new NotFoundException("Esa persona no pertenece a esta empresa"));
    }

    static Set<Role> requireValidRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new ValidationException("Una pertenencia necesita al menos un papel");
        }
        if (roles.contains(Role.GROUP_ADMIN)) {
            throw new ValidationException("El administrador del grupo no se asigna a una empresa");
        }
        return roles;
    }

    static void requireGroupAdminForAdministration(Actor actor) {
        if (!actor.isGroupAdmin()) {
            throw new ForbiddenException(
                    "Solo el administrador del grupo da o quita la administración de una empresa");
        }
    }

    /** La persona lista para administrar: con correo y con la contraseña que ya tiene o la que llega. */
    static User readyToAdminister(User user, Optional<String> password, PasswordHasher passwords) {
        if (user.email().isEmpty()) {
            throw new ValidationException(ADMINISTRATOR_NEEDS_EMAIL);
        }
        if (user.passwordHash().isPresent()) {
            return user;
        }
        String plain = password.filter(p -> !p.isBlank())
                .orElseThrow(() -> new ValidationException("Hace falta una contraseña para administrar"));
        return user.withPasswordHash(passwords.hash(Validation.requirePassword(plain)));
    }

    /** Si la persona ya no administra ninguna empresa (y no es la administradora del grupo), sus sesiones del panel dejan de valer. */
    static void closeSessionsIfNoLongerAdministering(User user, MembershipRepository memberships,
            AdminSessions sessions) {
        if (!user.groupAdmin() && companiesAdministeredBy(memberships, user.id()).isEmpty()) {
            sessions.closeAllOf(user.id());
        }
    }
}
