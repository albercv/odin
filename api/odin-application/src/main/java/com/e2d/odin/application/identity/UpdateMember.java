package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.OrganizationRepository;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;

public class UpdateMember {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final PasswordHasher passwords;
    private final AdminSessions sessions;

    public UpdateMember(UserRepository users, MembershipRepository memberships, OrganizationRepository organizations,
            PasswordHasher passwords, AdminSessions sessions) {
        this.users = users;
        this.memberships = memberships;
        this.organizations = organizations;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    public MemberView execute(Actor actor, CompanyId companyId, UserId userId, MemberChanges changes) {
        AccessPolicy.requireAdminOf(actor, companyId);
        Membership current = UserAdministration.requireMembership(memberships, companyId, userId);
        Membership updated = new Membership(userId, companyId,
                changes.roles().map(UserAdministration::requireValidRoles).orElse(current.roles()),
                changes.active().orElse(current.active()));
        requireNotActingOnSelf(actor, current, updated);
        if (touchesAdministration(current, updated)) {
            UserAdministration.requireGroupAdminForAdministration(actor);
        }
        String companyCode = organizations.findCompany(companyId)
                .orElseThrow(() -> new NotFoundException("Empresa no encontrada")).code();
        User user = personReadyFor(current, updated, changes.password(), userId);
        memberships.save(updated);
        if (losesAdministration(current, updated)) {
            UserAdministration.closeSessionsIfNoLongerAdministering(user, memberships, sessions);
        }
        return MemberView.of(user, updated, companyCode, actor);
    }

    /** Dar o quitar la administración, o activar o desactivar una pertenencia que la tiene. */
    private static boolean touchesAdministration(Membership current, Membership updated) {
        return current.isCompanyAdmin() != updated.isCompanyAdmin()
                || (current.isCompanyAdmin() && current.active() != updated.active());
    }

    private static void requireNotActingOnSelf(Actor actor, Membership current, Membership updated) {
        if (!actor.userId().equals(current.userId())) {
            return;
        }
        if (current.isCompanyAdmin() && !updated.isCompanyAdmin()) {
            throw new ValidationException("No puedes quitarte el papel con el que estás actuando");
        }
        if (current.active() && !updated.active()) {
            throw new ValidationException("No puedes desactivarte a ti mismo");
        }
    }

    /** Quien pasa a administrar necesita correo y contraseña; se guarda la que llega si aún no tenía. */
    private User personReadyFor(Membership current, Membership updated, Optional<String> password, UserId userId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        if (current.isCompanyAdmin() || !updated.isCompanyAdmin()) {
            if (password.filter(p -> !p.isBlank()).isPresent()) {
                throw new ValidationException(UserAdministration.WORKER_HAS_NO_PASSWORD);
            }
            return user;
        }
        User ready = UserAdministration.readyToAdminister(user, password, passwords);
        if (!ready.equals(user)) {
            users.save(ready);
        }
        return ready;
    }

    private static boolean losesAdministration(Membership current, Membership updated) {
        return current.isCompanyAdmin() && (!updated.isCompanyAdmin() || (current.active() && !updated.active()));
    }
}
