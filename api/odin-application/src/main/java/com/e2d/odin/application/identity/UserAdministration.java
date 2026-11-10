package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class UserAdministration {

    static final String WORKER_HAS_NO_PASSWORD = "Un trabajador no tiene contraseña: entra con su clave personal";

    private UserAdministration() {
    }

    /** Falla si otro usuario (distinto de {@code editing}, si lo hay) ya usa el correo. */
    static void requireEmailFree(UserRepository users, String email, Optional<UserId> editing) {
        users.findByEmail(email).filter(other -> editing.map(id -> !other.id().equals(id)).orElse(true))
                .ifPresent(other -> {
                    throw new ConflictException("Ya existe un usuario con ese correo");
                });
    }

    /** Persona con su pertenencia; hoy cada persona tiene como mucho una, y el administrador del grupo ninguna. */
    static UserSummary summarize(MembershipRepository memberships, User user) {
        Optional<Membership> membership = user.groupAdmin() ? Optional.empty()
                : memberships.listByUser(user.id()).stream().findFirst();
        return new UserSummary(user, membership);
    }

    /** Empresas donde la persona tiene una pertenencia activa como administrador de empresa. */
    static Set<CompanyId> companiesAdministeredBy(MembershipRepository memberships, UserId userId) {
        return memberships.listByUser(userId).stream().filter(m -> m.active() && m.isCompanyAdmin())
                .map(Membership::companyId).collect(Collectors.toSet());
    }

    /** Carga a la persona y exige que el actor la administre; si no existe, 404. */
    static UserSummary loadAdministrable(UserRepository users, MembershipRepository memberships, Actor actor,
            UserId id) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        UserSummary summary = summarize(memberships, user);
        summary.companyId().ifPresentOrElse(company -> AccessPolicy.requireAdminOf(actor, company),
                () -> AccessPolicy.requireGroupAdmin(actor));
        return summary;
    }
}
