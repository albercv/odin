package com.e2d.odin.application.document;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.identity.Actor;

public record UploadOutcome(DocumentEntry entry, boolean duplicate) {

    public Document document() {
        return entry.document();
    }

    /** La entrada de una subida del administrador: título, tamaño y huella; nunca el contenido. */
    public AuditEntry auditEntry(Actor actor, int sizeBytes) {
        return auditEntry(AuditEntry.by(actor, AuditAction.DOCUMENT_UPLOADED), sizeBytes);
    }

    /** Lo mismo sobre una entrada ya construida, para que el enlace ponga su propio autor. */
    public AuditEntry auditEntry(AuditEntry base, int sizeBytes) {
        return AuditSupport.onDocument(base, document())
                .with("sizeBytes", sizeBytes).with("sha256", document().sha256()).with("duplicate", duplicate);
    }
}
