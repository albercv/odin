package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessionRepository;
import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.SecretHasher;
import com.e2d.odin.application.port.TokenGenerator;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.shared.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class StoredAdminSessions implements AdminSessions {

    private static final int TOKEN_BYTES = 32;

    private final AdminSessionRepository repository;
    private final SecretHasher secrets;
    private final TokenGenerator tokens;
    private final SessionSettings settings;
    private final Clock clock;

    public StoredAdminSessions(AdminSessionRepository repository, SecretHasher secrets, TokenGenerator tokens,
            SessionSettings settings, Clock clock) {
        this.repository = repository;
        this.secrets = secrets;
        this.tokens = tokens;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public String open(UserId userId) {
        String token = tokens.randomToken(TOKEN_BYTES);
        Instant now = clock.instant();
        repository.save(new AdminSession(secrets.hash(token), userId, now, now));
        return token;
    }

    @Override
    public Optional<UserId> resolve(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String hash = secrets.hash(token);
        Instant now = clock.instant();
        Optional<AdminSession> found = repository.findByTokenHash(hash);
        if (found.isPresent() && found.get().lastSeenAt().plus(settings.idleTimeout()).isBefore(now)) {
            repository.delete(hash);
            return Optional.empty();
        }
        found.ifPresent(session -> repository.save(session.seenAt(now)));
        return found.map(AdminSession::userId);
    }

    @Override
    public void close(String token) {
        if (token != null) {
            repository.delete(secrets.hash(token));
        }
    }

    @Override
    public void closeAllOf(UserId userId) {
        repository.deleteByUser(userId);
    }
}
