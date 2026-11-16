package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AdminSessionRepository;
import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.AuditContext.AuditRequest;
import com.e2d.odin.application.port.SecretHasher;
import com.e2d.odin.application.port.TokenGenerator;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.identity.SessionExpiredException;
import com.e2d.odin.domain.identity.SessionExpiredException.Reason;
import com.e2d.odin.domain.shared.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class StoredAdminSessions implements AdminSessions {

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_USER_AGENT = 300;

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
    public String open(UserId userId, AuditRequest client) {
        String token = tokens.randomToken(TOKEN_BYTES);
        Instant now = clock.instant();
        repository.save(new AdminSession(UUID.randomUUID(), secrets.hash(token), userId, now, now,
                client.userAgent().map(ua -> ua.length() > MAX_USER_AGENT ? ua.substring(0, MAX_USER_AGENT) : ua),
                client.ipAddress(), now));
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
        if (found.isPresent()) {
            expireIfDue(found.get(), now);
        }
        found.ifPresent(session -> repository.save(session.seenAt(now)));
        return found.map(AdminSession::userId);
    }

    private void expireIfDue(AdminSession session, Instant now) {
        Reason reason = session.createdAt().plus(settings.absoluteTimeout()).isBefore(now) ? Reason.ABSOLUTE
                : session.lastSeenAt().plus(settings.idleTimeout()).isBefore(now) ? Reason.IDLE : null;
        if (reason != null) {
            repository.delete(session.tokenHash());
            throw new SessionExpiredException(ResolveActor.INVALID_CREDENTIALS, session.userId(), reason);
        }
    }

    @Override
    public Optional<AdminSession> find(String token) {
        return token == null ? Optional.empty() : repository.findByTokenHash(secrets.hash(token));
    }

    @Override
    public List<AdminSession> ofUser(UserId userId) {
        return repository.findByUser(userId);
    }

    @Override
    public boolean closeOwn(UserId userId, UUID id) {
        return repository.deleteByIdAndUser(id, userId) > 0;
    }

    @Override
    public void close(String token) {
        if (token != null) {
            repository.delete(secrets.hash(token));
        }
    }

    @Override
    public int closeAllOf(UserId userId) {
        return repository.deleteByUser(userId);
    }
}
