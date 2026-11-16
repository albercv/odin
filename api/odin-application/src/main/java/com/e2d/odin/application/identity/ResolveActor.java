package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.SecretHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.SecretComparison;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.SessionExpiredException;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UnauthorizedException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Set;

public class ResolveActor {

    public static final String INVALID_CREDENTIALS = "Credenciales no válidas";

    private static final String KEY_PREFIX = "odin_";
    private static final int PREFIX_LENGTH = 8;
    private static final int SECRET_START = KEY_PREFIX.length() + PREFIX_LENGTH + 1;

    private final AccessKeyRepository keys;
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final AdminSessions sessions;
    private final SecretHasher secrets;
    private final AuditLog audit;

    public ResolveActor(AccessKeyRepository keys, UserRepository users, MembershipRepository memberships,
            AdminSessions sessions, SecretHasher secrets, AuditLog audit) {
        this.audit = audit;
        this.keys = keys;
        this.users = users;
        this.memberships = memberships;
        this.sessions = sessions;
        this.secrets = secrets;
    }

    public Actor fromAccessKey(String plainKey) {
        AccessKey key = findKey(plainKey);
        return worker(key.userId(), key.companyId());
    }

    /** El trabajador activo de una empresa, para quien llega con un enlace en vez de con su clave. */
    public Actor worker(UserId userId, CompanyId companyId) {
        User user = users.findById(userId).filter(User::active).orElseThrow(ResolveActor::unauthorized);
        memberships.find(userId, companyId).filter(m -> m.active() && m.isWorker())
                .orElseThrow(ResolveActor::unauthorized);
        return new Actor(user.id(), user.displayName(), Role.WORKER, Set.of(companyId));
    }

    public Actor fromSessionToken(String token) {
        try {
            return sessions.resolve(token).flatMap(users::findById).filter(User::active)
                    .map(this::administratorActor).orElseThrow(ResolveActor::unauthorized);
        } catch (SessionExpiredException e) {
            recordExpiry(e);
            throw e;
        }
    }

    private void recordExpiry(SessionExpiredException e) {
        users.findById(e.userId()).ifPresent(user -> audit.record(AuditEntry
                .by(new Actor(user.id(), user.displayName(), roleOf(user), Set.of()), AuditAction.SESSION_EXPIRED)
                .with("reason", e.reason().name())));
    }

    private Role roleOf(User user) {
        return user.groupAdmin() ? Role.GROUP_ADMIN
                : UserAdministration.companiesAdministeredBy(memberships, user.id()).isEmpty() ? Role.WORKER
                        : Role.COMPANY_ADMIN;
    }

    private Actor administratorActor(User user) {
        if (user.groupAdmin()) {
            return new Actor(user.id(), user.displayName(), Role.GROUP_ADMIN, Set.of());
        }
        Set<CompanyId> companies = UserAdministration.companiesAdministeredBy(memberships, user.id());
        if (companies.isEmpty()) {
            throw unauthorized();
        }
        return new Actor(user.id(), user.displayName(), Role.COMPANY_ADMIN, companies);
    }

    private AccessKey findKey(String plainKey) {
        boolean wellFormed = plainKey != null && plainKey.startsWith(KEY_PREFIX)
                && plainKey.length() > SECRET_START && plainKey.charAt(SECRET_START - 1) == '_';
        if (!wellFormed) {
            throw unauthorized();
        }
        String prefix = plainKey.substring(KEY_PREFIX.length(), SECRET_START - 1);
        String secretHash = secrets.hash(plainKey.substring(SECRET_START));
        return keys.findByPrefix(prefix)
                .filter(k -> k.revokedAt().isEmpty() && SecretComparison.equal(k.secretHash(), secretHash))
                .orElseThrow(ResolveActor::unauthorized);
    }

    private static UnauthorizedException unauthorized() {
        return new UnauthorizedException(INVALID_CREDENTIALS);
    }
}
