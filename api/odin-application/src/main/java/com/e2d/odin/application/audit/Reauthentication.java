package com.e2d.odin.application.audit;

import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.application.port.PasswordHasher;
import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.ForbiddenException;

/** Volver a identificarse con la contraseña actual; si es mala deja la entrada negada y responde 403. */
public final class Reauthentication {

    private Reauthentication() {
    }

    public static void require(UserRepository users, PasswordHasher passwords, AuditLog audit, Actor actor,
            String password, String purpose) {
        boolean correct = password != null && users.findById(actor.userId()).flatMap(User::passwordHash)
                .filter(hash -> passwords.matches(password, hash)).isPresent();
        if (!correct) {
            audit.recordDenied(AuditEntry.by(actor, AuditAction.REAUTHENTICATION_FAILED).denied()
                    .with("purpose", purpose));
            throw new ForbiddenException("La contraseña no es correcta");
        }
    }
}
