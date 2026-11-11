package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.InstallationSettings;
import com.e2d.odin.application.shared.InstallationToken;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.ConflictException;

public class RecoverGroupAdmin {

    private final UserRepository users;
    private final PasswordHasher passwords;
    private final AdminSessions sessions;
    private final InstallationSettings settings;

    public RecoverGroupAdmin(UserRepository users, PasswordHasher passwords, AdminSessions sessions,
            InstallationSettings settings) {
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.settings = settings;
    }

    public void execute(String setupToken, String newPassword) {
        InstallationToken.require(settings, setupToken);
        User admin = users.findGroupAdmin()
                .orElseThrow(() -> new ConflictException("La instalación no está configurada"));
        String hash = passwords.hash(Validation.requirePassword(newPassword));
        users.save(admin.withActive(true).withPasswordHash(hash));
        sessions.closeAllOf(admin.id());
    }
}
