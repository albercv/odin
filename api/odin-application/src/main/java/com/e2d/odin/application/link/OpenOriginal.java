package com.e2d.odin.application.link;

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

    public OpenOriginal(ResolveLink resolve, OriginalFileStore files, DocumentRepository documents) {
        this.resolve = resolve;
        this.files = files;
        this.documents = documents;
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
        return read(document);
    }

    private OriginalFile read(Document document) {
        return new OriginalFile(document.filename(), document.format(), files.read(document.sha256()));
    }
}
