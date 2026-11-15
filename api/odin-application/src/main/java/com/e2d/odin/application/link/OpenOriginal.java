package com.e2d.odin.application.link;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OriginalFileStore;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;

/** El original de un documento: con el enlace de quien lo pidió, o con la sesión del administrador. */
public class OpenOriginal {

    private final ResolveLink resolve;
    private final OriginalFileStore files;
    private final DocumentRepository documents;
    private final AuditLog audit;

    public OpenOriginal(ResolveLink resolve, OriginalFileStore files, DocumentRepository documents,
            AuditLog audit) {
        this.resolve = resolve;
        this.files = files;
        this.documents = documents;
        this.audit = audit;
    }

    public OriginalFile execute(String token, String clientAddress) {
        var target = resolve.execute(token, clientAddress, AccessLinkKind.ORIGINAL).target();
        if (target instanceof LinkTarget.OriginalTarget original) {
            return read(original.document());
        }
        throw new NotFoundException("Enlace no encontrado");
    }

    public OriginalFile execute(Actor admin, DocumentId id) {
        AccessPolicy.requireAdmin(admin);
        Document document = documents.findById(admin.scope(), ProjectVisibility.all(), id)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        OriginalFile original = read(document);
        audit.record(AuditSupport.onDocument(AuditEntry.by(admin, AuditAction.DOCUMENT_ORIGINAL_DOWNLOADED), document));
        return original;
    }

    private OriginalFile read(Document document) {
        return new OriginalFile(document.filename(), document.format(), files.read(document.sha256()));
    }
}
