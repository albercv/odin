package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminChatKeyRepository;
import com.e2d.odin.application.port.AuditSettingsRepository;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.SecretHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.SecretComparison;
import com.e2d.odin.domain.audit.AuditSettings;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.AdminChatKey;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UnauthorizedException;
import java.time.Clock;
import java.util.Set;

/** En cada llamada: formato, huella, revocación, caducidad, persona activa, sigue administrando el ámbito e interruptores. */
public class ResolveAdminChatKey {

    public static final String CHAT_DISABLED = "La consulta del registro desde el chat está desactivada";

    private final AdminChatKeyRepository keys;
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final AuditSettingsRepository settings;
    private final SecretHasher secrets;
    private final Clock clock;

    public ResolveAdminChatKey(AdminChatKeyRepository keys, UserRepository users, MembershipRepository memberships,
            AuditSettingsRepository settings, SecretHasher secrets, Clock clock) {
        this.keys = keys;
        this.users = users;
        this.memberships = memberships;
        this.settings = settings;
        this.secrets = secrets;
        this.clock = clock;
    }

    public ChatAccess execute(String token) {
        AdminChatToken.Parsed parsed = AdminChatToken.parse(token).orElseThrow(ResolveAdminChatKey::unauthorized);
        String hash = secrets.hash(parsed.secret());
        AdminChatKey key = keys.findByPrefix(parsed.prefix())
                .filter(k -> SecretComparison.equal(k.tokenHash(), hash) && k.activeAt(clock.instant()))
                .orElseThrow(ResolveAdminChatKey::unauthorized);
        User user = users.findById(key.userId()).filter(User::active).orElseThrow(ResolveAdminChatKey::unauthorized);
        Actor actor = actorOf(key, user);
        AuditSettings current = settings.get();
        if (!(current.enabled() && current.chatEnabled())) {
            throw new ForbiddenException(CHAT_DISABLED);
        }
        return new ChatAccess(actor, key);
    }

    private Actor actorOf(AdminChatKey key, User user) {
        if (key.companyId().isEmpty()) {
            if (!user.groupAdmin()) {
                throw lostRole();
            }
            return new Actor(user.id(), user.displayName(), Role.GROUP_ADMIN, Set.of());
        }
        CompanyId company = key.companyId().get();
        memberships.find(user.id(), company).filter(m -> m.active() && m.isCompanyAdmin())
                .orElseThrow(ResolveAdminChatKey::lostRole);
        return new Actor(user.id(), user.displayName(), Role.COMPANY_ADMIN, Set.of(company));
    }

    private static ForbiddenException lostRole() {
        return new ForbiddenException("Ya no administras el ámbito de esta clave");
    }

    private static UnauthorizedException unauthorized() {
        return new UnauthorizedException(ResolveActor.INVALID_CREDENTIALS);
    }
}
