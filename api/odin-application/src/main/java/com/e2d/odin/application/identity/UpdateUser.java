package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
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
    private final AuditLog audit;

    public UpdateUser(UserRepository users, MembershipRepository memberships, AdminSessions sessions,
            AuditLog audit) {
        this.audit = audit;
        this.users = users;
        this.memberships = memberships;
        this.sessions = sessions;
    }

    public User execute(Actor actor, UserId id, UserChanges changes) {
        List<Membership> theirMemberships = memberships.listByUser(id);
        requireMayEdit(actor, id, theirMemberships);
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        UserAdministration.requireGroupAdminToActOn(actor, user);
        boolean active = changes.active().orElse(user.active());
        requireMayChangeActive(actor, user, active);
        if (user.active() && !active) {
            UserAdministration.requireAnotherActiveGroupAdmin(users, user);
        }
        String firstName = changes.firstName().map(UserAdministration::requireFirstName).orElse(user.firstName());
        Optional<String> lastName = changes.lastName().map(UserAdministration::requireLastName).or(user::lastName);
        Optional<String> email = changes.email().map(e -> newEmail(actor, user, theirMemberships, e)).orElse(user.email());
        User updated = user.withNames(firstName, lastName).withEmail(email).withActive(active);
        users.save(updated);
        audit.record(userUpdated(actor, user, updated));
        if (user.active() && !active) {
            sessions.closeAllOf(id);
        }
        return updated;
    }

    /** Nombre y correo son datos personales: solo se anota que cambiaron. */
    private static AuditEntry userUpdated(Actor actor, User before, User after) {
        AuditEntry entry = AuditEntry.by(actor, AuditAction.USER_UPDATED).on("USER", before.id().value().toString(), null)
                .with("nameChanged", !before.firstName().equals(after.firstName()) || !before.lastName().equals(after.lastName()))
                .with("emailChanged", !before.email().equals(after.email()));
        if (before.active() != after.active()) {
            entry = entry.with("before", java.util.Map.of("active", before.active()))
                    .with("after", java.util.Map.of("active", after.active()));
        }
        return entry;
    }

    /** El administrador del grupo edita a cualquiera y cada persona a sí misma; el de empresa, solo a quien no está en otras empresas. */
    private static void requireMayEdit(Actor actor, UserId id, List<Membership> theirMemberships) {
        if (actor.isGroupAdmin() || actor.userId().equals(id)) {
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

    /** El correo es la puerta al panel: el de un administrador de empresa solo lo cambia el administrador del grupo o él mismo. */
    private static void requireMayChangeEmail(Actor actor, User user, List<Membership> theirMemberships) {
        if (!actor.isGroupAdmin() && !actor.userId().equals(user.id())
                && theirMemberships.stream().anyMatch(Membership::isCompanyAdmin)) {
            throw new ForbiddenException("Solo el administrador del grupo puede editar a esta persona");
        }
    }

    private Optional<String> newEmail(Actor actor, User user, List<Membership> theirMemberships, String raw) {
        requireMayChangeEmail(actor, user, theirMemberships);
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
