package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.time.Instant;

/**
 * Quita a una persona de una empresa y revoca las claves de esa pertenencia, para que no vuelvan a valer si la
 * persona se añade otra vez. La persona sigue existiendo, y sus otras pertenencias, también.
 */
public class RemoveMember {

    private final MembershipRepository memberships;
    private final AccessKeyRepository keys;
    private final AdminSessions sessions;
    private final Clock clock;

    public RemoveMember(MembershipRepository memberships, AccessKeyRepository keys, AdminSessions sessions,
            Clock clock) {
        this.memberships = memberships;
        this.keys = keys;
        this.sessions = sessions;
        this.clock = clock;
    }

    public void execute(Actor actor, CompanyId companyId, UserId userId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        Membership membership = UserAdministration.requireMembership(memberships, companyId, userId);
        if (actor.userId().equals(userId)) {
            throw new ValidationException("No puedes quitarte de la empresa");
        }
        if (membership.isCompanyAdmin()) {
            UserAdministration.requireGroupAdminForAdministration(actor);
        }
        Instant now = clock.instant();
        keys.listByMembership(userId, companyId).stream().filter(key -> key.revokedAt().isEmpty())
                .forEach(key -> keys.save(key.revoked(now)));
        memberships.delete(userId, companyId, now);
        UserAdministration.closeSessionsIfNoLongerAdministering(memberships, sessions, userId);
    }
}
