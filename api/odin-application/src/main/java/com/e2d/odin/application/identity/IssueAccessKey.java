package com.e2d.odin.application.identity;

import com.e2d.odin.application.port.AccessKeyRepository;
import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.MembershipRepository;
import com.e2d.odin.application.port.SecretHasher;
import com.e2d.odin.application.port.TokenGenerator;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.application.shared.Validation;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Clock;
import java.util.Optional;

public class IssueAccessKey {

    private static final int PREFIX_BYTES = 6;
    private static final int SECRET_BYTES = 32;

    private final AccessKeyRepository keys;
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final SecretHasher secrets;
    private final TokenGenerator tokens;
    private final IdGenerator ids;
    private final Clock clock;

    public IssueAccessKey(AccessKeyRepository keys, UserRepository users, MembershipRepository memberships,
            SecretHasher secrets, TokenGenerator tokens, IdGenerator ids, Clock clock) {
        this.keys = keys;
        this.users = users;
        this.memberships = memberships;
        this.secrets = secrets;
        this.tokens = tokens;
        this.ids = ids;
        this.clock = clock;
    }

    public IssuedKey execute(Actor actor, UserId userId, String label) {
        UserSummary target = UserAdministration.loadAdministrable(users, memberships, actor, userId);
        Membership membership = target.membership().filter(m -> m.isWorker() && target.active())
                .orElseThrow(() -> new ValidationException("Solo los trabajadores activos pueden tener claves"));
        String prefix = newUniquePrefix();
        String secret = tokens.randomToken(SECRET_BYTES);
        AccessKeyId id = new AccessKeyId(ids.newId());
        keys.save(new AccessKey(id, userId, membership.companyId(), Validation.requireText(label, "la etiqueta"),
                prefix, secrets.hash(secret), clock.instant(), Optional.empty()));
        return new IssuedKey(id, prefix, "odin_" + prefix + "_" + secret);
    }

    private String newUniquePrefix() {
        String prefix;
        do {
            prefix = tokens.randomToken(PREFIX_BYTES);
        } while (keys.findByPrefix(prefix).isPresent());
        return prefix;
    }
}
