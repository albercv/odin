package com.e2d.odin.application.audit;

import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.AuditSettingsRepository;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.audit.AuditSettings;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;

/**
 * Enciende, apaga o ajusta el registro. Pide la contraseña actual. Encender se anota después de encender y apagar
 * antes de apagar, para que las dos entradas queden en la cadena.
 */
public class UpdateAuditSettings {

    private static final String TARGET = "AUDIT_SETTINGS";

    private final AuditSettingsRepository settings;
    private final AuditLog audit;
    private final UserRepository users;
    private final PasswordHasher passwords;

    public UpdateAuditSettings(AuditSettingsRepository settings, AuditLog audit, UserRepository users,
            PasswordHasher passwords) {
        this.settings = settings;
        this.audit = audit;
        this.users = users;
        this.passwords = passwords;
    }

    public AuditSettings execute(Actor actor, String password, boolean enabled, String usagePolicyReference,
            boolean chatEnabled) {
        AccessPolicy.requireGroupAdmin(actor);
        requirePassword(actor, password);
        AuditSettings current = settings.get();
        Optional<String> kept = Optional.ofNullable(usagePolicyReference).map(String::strip)
                .filter(s -> !s.isEmpty()).or(current::usagePolicyReference);
        if (enabled && kept.isEmpty()) {
            throw new ValidationException("Indica dónde está publicada la política de uso antes de encender el registro");
        }
        AuditEntry entry = AuditEntry.by(actor, action(current, enabled)).on(TARGET, "main", null)
                .with("enabled", enabled).with("chatEnabled", chatEnabled)
                .with("policyReferenceChanged", !kept.equals(current.usagePolicyReference()));
        boolean disabling = current.enabled() && !enabled;
        if (disabling) {
            audit.record(entry);
        }
        settings.update(enabled, kept, chatEnabled, actor.userId());
        if (!disabling) {
            audit.record(entry);
        }
        return settings.get();
    }

    private static AuditAction action(AuditSettings current, boolean enabled) {
        if (enabled == current.enabled()) {
            return AuditAction.AUDIT_SETTINGS_UPDATED;
        }
        return enabled ? AuditAction.AUDIT_ENABLED : AuditAction.AUDIT_DISABLED;
    }

    private void requirePassword(Actor actor, String password) {
        boolean correct = password != null && users.findById(actor.userId()).flatMap(User::passwordHash)
                .filter(hash -> passwords.matches(password, hash)).isPresent();
        if (!correct) {
            audit.recordDenied(AuditEntry.by(actor, AuditAction.REAUTHENTICATION_FAILED).denied()
                    .with("purpose", "AUDIT_SETTINGS"));
            throw new ForbiddenException("La contraseña no es correcta");
        }
    }
}
