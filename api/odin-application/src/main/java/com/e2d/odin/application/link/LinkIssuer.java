package com.e2d.odin.application.link;

import com.e2d.odin.application.port.AccessLinkRepository;
import com.e2d.odin.application.port.IdGenerator;
import com.e2d.odin.application.port.SecretHasher;
import com.e2d.odin.application.port.TokenGenerator;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.link.AccessLink;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** Crea el enlace: un token de 256 bits que solo se guarda como hash y una URL que no lleva nada más que él. */
public class LinkIssuer {

    private static final int TOKEN_BYTES = 32;

    private final AccessLinkRepository links;
    private final TokenGenerator tokens;
    private final SecretHasher secrets;
    private final IdGenerator ids;
    private final Clock clock;
    private final LinkSettings settings;

    public LinkIssuer(AccessLinkRepository links, TokenGenerator tokens, SecretHasher secrets, IdGenerator ids, Clock clock,
            LinkSettings settings) {
        this.links = links;
        this.tokens = tokens;
        this.secrets = secrets;
        this.ids = ids;
        this.clock = clock;
        this.settings = settings;
    }

    /** {@code pathBeforeToken} precede al token en la URL; {@code fragment} va después, con su almohadilla, si lo hay. */
    IssuedLink issue(AccessLinkKind kind, Actor actor, DocumentId target, String pathBeforeToken, String fragment) {
        boolean upload = kind == AccessLinkKind.UPLOAD;
        return issue(kind, actor, upload ? Optional.of(target) : Optional.empty(), Optional.empty(),
                upload ? Optional.empty() : Optional.of(target), pathBeforeToken, fragment);
    }

    /** El enlace de subida a un proyecto: lleva el proyecto y ningún caso. */
    IssuedLink issueForProject(Actor actor, ProjectId project, String pathBeforeToken) {
        return issue(AccessLinkKind.UPLOAD, actor, Optional.empty(), Optional.of(project), Optional.empty(),
                pathBeforeToken, "");
    }

    private IssuedLink issue(AccessLinkKind kind, Actor actor, Optional<DocumentId> caseId, Optional<ProjectId> projectId,
            Optional<DocumentId> documentId, String pathBeforeToken, String fragment) {
        String prefix = settings.urlOf(pathBeforeToken);
        String token = tokens.randomToken(TOKEN_BYTES);
        Instant now = clock.instant();
        AccessLink link = new AccessLink(ids.newId(), kind, actor.requireCompany(), actor.userId(), caseId, projectId,
                documentId, now, now.plus(settings.ttl()), Optional.empty(), 0);
        links.save(link, secrets.hash(token));
        return new IssuedLink(prefix + token + fragment, link.expiresAt());
    }
}
