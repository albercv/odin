package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.List;
import java.util.Optional;

/** Cambia a la persona: nombre, correo y si está activa. Lo que toca a una empresa vive en {@link UpdateMember}. */
public class UpdateUser {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final AdminSessions sessions;

    public UpdateUser(UserRepository users, MembershipRepository memberships, AdminSessions sessions) {
        this.users = users;
        this.memberships = memberships;
        this.sessions = sessions;
    }

    public User execute(Actor actor, UserId id, UserChanges changes) {
        List<Membership> theirMemberships = memberships.listByUser(id);
        requireMayEdit(actor, theirMemberships);
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        boolean active = changes.active().orElse(user.active());
        requireMayChangeActive(actor, user, active);
        String name = changes.displayName().map(n -> Validation.requireText(n, "el nombre")).orElse(user.displayName());
        Optional<String> email = changes.email().map(e -> newEmail(user, theirMemberships, e)).orElse(user.email());
        User updated = user.updated(name, email, active);
        users.save(updated);
        if (user.active() && !active) {
            sessions.closeAllOf(id);
        }
        return updated;
    }

    /** El administrador del grupo edita a cualquiera; el de empresa, solo a quien no está en otras empresas. */
    private static void requireMayEdit(Actor actor, List<Membership> theirMemberships) {
        if (actor.isGroupAdmin()) {
            return;
        }
        if (theirMemberships.stream().noneMatch(m -> actor.administers(m.companyId()))) {
            throw new ForbiddenException("No tienes permiso sobre esta empresa");
        }
        if (!theirMemberships.stream().allMatch(m -> actor.administers(m.companyId()))) {
            throw new ForbiddenException("Solo el administrador del grupo puede editar a esta persona");
        }
    }

    private static void requireMayChangeActive(Actor actor, User user, boolean active) {
        if (active == user.active()) {
            return;
        }
        if (!actor.isGroupAdmin()) {
            throw new ForbiddenException("Solo el administrador del grupo activa o desactiva a una persona");
        }
        if (!active && actor.userId().equals(user.id())) {
            throw new ValidationException("No puedes desactivarte a ti mismo");
        }
    }

    private Optional<String> newEmail(User user, List<Membership> theirMemberships, String raw) {
        if (raw.isBlank()) {
            if (user.groupAdmin() || theirMemberships.stream().anyMatch(Membership::isCompanyAdmin)) {
                throw new ValidationException(UserAdministration.ADMINISTRATOR_NEEDS_EMAIL);
            }
            return Optional.empty();
        }
        String email = Validation.normalizeEmail(raw);
        UserAdministration.requireEmailFree(users, email, Optional.of(user.id()));
        return Optional.of(email);
    }
}
