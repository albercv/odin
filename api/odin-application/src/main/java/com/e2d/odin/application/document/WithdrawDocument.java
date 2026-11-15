package com.e2d.odin.application.document;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.time.Clock;
import java.util.Optional;

/** Retirar oculta el documento a los trabajadores sin borrar el original, las páginas ni sus relaciones. */
public class WithdrawDocument {

    private final DocumentRepository documents;
    private final Clock clock;
    private final AuditLog audit;

    public WithdrawDocument(DocumentRepository documents, Clock clock, AuditLog audit) {
        this.documents = documents;
        this.clock = clock;
        this.audit = audit;
    }

    public DocumentEntry execute(Actor actor, DocumentId documentId) {
        AccessPolicy.requireAdmin(actor);
        DocumentEntry entry = documents.findEntryIncludingWithdrawn(actor.scope(), ProjectVisibility.all(), documentId)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        AccessPolicy.requireAdminOf(actor, entry.document().companyId());
        if (entry.document().withdrawn()) {
            return entry;
        }
        documents.markWithdrawal(entry.document().withWithdrawal(Optional.of(clock.instant()),
                Optional.of(actor.userId())));
        audit.record(AuditSupport.onDocument(AuditEntry.by(actor, AuditAction.DOCUMENT_WITHDRAWN),
                entry.document()));
        return documents.findEntryIncludingWithdrawn(actor.scope(), ProjectVisibility.all(), documentId).orElseThrow();
    }
}
