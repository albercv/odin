package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.application.port.AdminSessions;
import com.e2d.odin.application.port.SecretHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.SecretComparison;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UnauthorizedException;

public class ResolveActor {

    public static final String INVALID_CREDENTIALS = "Credenciales no válidas";

    private static final String KEY_PREFIX = "odin_";
    private static final int PREFIX_LENGTH = 8;
    private static final int SECRET_START = KEY_PREFIX.length() + PREFIX_LENGTH + 1;

    private final AccessKeyRepository keys;
    private final UserRepository users;
    private final AdminSessions sessions;
    private final SecretHasher secrets;

    public ResolveActor(AccessKeyRepository keys, UserRepository users, AdminSessions sessions,
            SecretHasher secrets) {
        this.keys = keys;
        this.users = users;
        this.sessions = sessions;
        this.secrets = secrets;
    }

    public Actor fromAccessKey(String plainKey) {
        AccessKey key = findKey(plainKey);
        User user = users.findById(key.userId()).filter(User::active).orElseThrow(ResolveActor::unauthorized);
        if (user.role() != Role.WORKER) {
            throw unauthorized();
        }
        return toActor(user);
    }

    public Actor fromSessionToken(String token) {
        return sessions.resolve(token).flatMap(users::findById).filter(User::active).map(ResolveActor::toActor)
                .orElseThrow(ResolveActor::unauthorized);
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

    private static Actor toActor(User user) {
        return new Actor(user.id(), user.displayName(), user.role(), user.companyId());
    }

    private static UnauthorizedException unauthorized() {
        return new UnauthorizedException(INVALID_CREDENTIALS);
    }
}
