package com.e2d.odin.application.identity;

import com.e2d.odin.application.audit.Reauthentication;
import com.e2d.odin.application.port.AdminChatKeyRepository;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.AuditSettingsRepository;
import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.SecretHasher;
import com.e2d.odin.application.port.TokenGenerator;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.audit.AuditSettings;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.AdminChatKey;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Emite la clave del administrador para el chat (30 días), vuelve a pedir la contraseña y revoca la anterior. */
public class IssueAdminChatKey {

    public static final Duration VALIDITY = Duration.ofDays(30);
    private static final int SECRET_BYTES = 32;
    private static final String TARGET = "ADMIN_CHAT_KEY";

    private final AdminChatKeyRepository keys;
    private final UserRepository users;
    private final AuditSettingsRepository settings;
    private final PasswordHasher passwords;
    private final SecretHasher secrets;
    private final TokenGenerator tokens;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog audit;

    public IssueAdminChatKey(AdminChatKeyRepository keys, UserRepository users, AuditSettingsRepository settings,
            PasswordHasher passwords, SecretHasher secrets, TokenGenerator tokens, IdGenerator ids, Clock clock,
            AuditLog audit) {
        this.keys = keys;
        this.users = users;
        this.settings = settings;
        this.passwords = passwords;
        this.secrets = secrets;
        this.tokens = tokens;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
    }

    public IssuedAdminChatKey execute(Actor actor, String password, Optional<CompanyId> requested) {
        AccessPolicy.requireAdmin(actor);
        Reauthentication.require(users, passwords, audit, actor, password, TARGET);
        AuditSettings current = settings.get();
        if (!(current.enabled() && current.chatEnabled())) {
            throw new ConflictException(
                    "El registro y la consulta desde el chat tienen que estar activados para emitir la clave");
        }
        Optional<CompanyId> scope = scopeOf(actor, requested);
        Instant now = clock.instant();
        keys.listActiveOf(actor.userId(), now).forEach(old -> {
            keys.save(old.revoked(now));
            audit.record(entry(actor, AuditAction.ADMIN_CHAT_KEY_REVOKED, old).with("reason", "REPLACED"));
        });
        String prefix = newUniquePrefix();
        String secret = tokens.randomToken(SECRET_BYTES);
        AdminChatKey key = new AdminChatKey(ids.newId(), actor.userId(), scope, prefix, secrets.hash(secret), now,
                now.plus(VALIDITY), Optional.empty());
        keys.save(key);
        audit.record(entry(actor, AuditAction.ADMIN_CHAT_KEY_ISSUED, key).with("scope",
                scope.isPresent() ? "COMPANY" : "GROUP"));
        return new IssuedAdminChatKey(key.id(), prefix, AdminChatToken.format(prefix, secret), key.expiresAt());
    }

    static AuditEntry entry(Actor actor, AuditAction action, AdminChatKey key) {
        AuditEntry entry = AuditEntry.by(actor, action).on(TARGET, key.id().toString(), null)
                .with("prefix", key.prefix());
        return key.companyId().map(entry::onCompany).orElse(entry);
    }

    private Optional<CompanyId> scopeOf(Actor actor, Optional<CompanyId> requested) {
        if (actor.isGroupAdmin()) {
            if (requested.isPresent()) {
                throw new ValidationException(
                        "El administrador del grupo emite claves de ámbito de grupo: no indiques empresa");
            }
            return Optional.empty();
        }
        CompanyId company = requested.orElseGet(() -> {
            if (actor.companies().size() != 1) {
                throw new ValidationException("Indica la empresa de la clave");
            }
            return actor.companies().iterator().next();
        });
        if (!actor.administers(company)) {
            audit.recordDenied(AuditEntry.by(actor, AuditAction.ADMIN_CHAT_KEY_ISSUED).denied().onCompany(company));
            throw new ForbiddenException("No tienes permiso sobre esta empresa");
        }
        return Optional.of(company);
    }

    private String newUniquePrefix() {
        String prefix;
        do {
            prefix = tokens.randomToken(6);
        } while (keys.findByPrefix(prefix).isPresent());
        return prefix;
    }
}
