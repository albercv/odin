package com.e2d.odin.application.identity;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.List;
import java.util.Optional;

public class RecoverGroupAdmin {

    private final UserRepository users;
    private final PasswordHasher passwords;
    private final AdminSessions sessions;
    private final InstallationSettings settings;
    private final AuditLog audit;

    public RecoverGroupAdmin(UserRepository users, PasswordHasher passwords, AdminSessions sessions,
            InstallationSettings settings, AuditLog audit) {
        this.audit = audit;
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.settings = settings;
    }

    public void execute(String setupToken, Optional<String> email, String newPassword) {
        AuditSupport.requireInstallationToken(settings, setupToken, audit, AuditAction.ADMIN_PASSWORD_RECOVERED);
        User admin = chooseAdmin(email.filter(e -> !e.isBlank()).map(Validation::normalizeEmail));
        String hash = passwords.hash(Validation.requirePassword(newPassword));
        users.save(admin.withActive(true).withPasswordHash(hash));
        audit.record(AuditEntry.byInstaller(AuditAction.ADMIN_PASSWORD_RECOVERED).on("USER", admin.id().value().toString(), null));
        sessions.closeAllOf(admin.id());
    }

    private User chooseAdmin(Optional<String> email) {
        List<User> admins = users.listGroupAdmins();
        if (admins.isEmpty()) {
            throw new ConflictException("La instalación no está configurada");
        }
        if (email.isPresent()) {
            return users.findGroupAdminByEmail(email.get()).orElseThrow(
                    () -> new NotFoundException("No hay ningún administrador del grupo con ese correo"));
        }
        if (admins.size() > 1) {
            throw new ValidationException("Indica el correo del administrador del grupo");
        }
        return admins.get(0);
    }
}
