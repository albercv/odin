package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessionRepository;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.SecretHasher;
import com.e2d.odin.application.port.TokenGenerator;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UnauthorizedException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

public class LogInAdmin {

    private static final int TOKEN_BYTES = 32;

    private final UserRepository users;
    private final AdminSessionRepository sessions;
    private final PasswordHasher passwords;
    private final SecretHasher secrets;
    private final TokenGenerator tokens;
    private final Clock clock;

    public LogInAdmin(UserRepository users, AdminSessionRepository sessions, PasswordHasher passwords,
            SecretHasher secrets, TokenGenerator tokens, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.secrets = secrets;
        this.tokens = tokens;
        this.clock = clock;
    }

    public NewSession execute(String email, String password) {
        User user = email == null || password == null ? null
                : users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).filter(u -> canLogIn(u, password))
                        .orElse(null);
        if (user == null) {
            throw new UnauthorizedException(ResolveActor.INVALID_CREDENTIALS);
        }
        String token = tokens.randomToken(TOKEN_BYTES);
        Instant now = clock.instant();
        sessions.save(new AdminSession(secrets.hash(token), user.id(), now, now));
        return new NewSession(token, user.id());
    }

    private boolean canLogIn(User user, String password) {
        return user.active() && user.role() != Role.WORKER
                && user.passwordHash().filter(hash -> passwords.matches(password, hash)).isPresent();
    }
}
