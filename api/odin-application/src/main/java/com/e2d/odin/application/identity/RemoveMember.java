package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;

/** Quita a una persona de una empresa. La persona sigue existiendo, y sus otras pertenencias, también. */
public class RemoveMember {

    private final MembershipRepository memberships;
    private final AdminSessions sessions;
    private final Clock clock;

    public RemoveMember(MembershipRepository memberships, AdminSessions sessions, Clock clock) {
        this.memberships = memberships;
        this.sessions = sessions;
        this.clock = clock;
    }

    public void execute(Actor actor, CompanyId companyId, UserId userId) {
        AccessPolicy.requireAdminOf(actor, companyId);
        Membership membership = UserAdministration.requireMembership(memberships, companyId, userId);
        if (actor.userId().equals(userId)) {
            throw new ValidationException("No puedes borrarte a ti mismo");
        }
        if (membership.isCompanyAdmin()) {
            UserAdministration.requireGroupAdminForAdministration(actor);
        }
        memberships.delete(userId, companyId, clock.instant());
        UserAdministration.closeSessionsIfNoLongerAdministering(memberships, sessions, userId);
    }
}
