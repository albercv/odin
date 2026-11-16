package com.e2d.odin.application.audit;

import com.e2d.odin.application.port.AuditReader;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.identity.Actor;
import java.util.OptionalLong;

/** El sello guardado sigue valiendo si ese eslabón no ha cambiado y la cadena hasta él es íntegra. */
public class CheckAuditSeal {

    private final AuditReader reader;

    public CheckAuditSeal(AuditReader reader) {
        this.reader = reader;
    }

    public boolean execute(Actor actor, long id, String hash) {
        AccessPolicy.requireAdmin(actor);
        OptionalLong broken = reader.firstBroken();
        return reader.sealMatches(id, hash) && (broken.isEmpty() || broken.getAsLong() > id);
    }
}
