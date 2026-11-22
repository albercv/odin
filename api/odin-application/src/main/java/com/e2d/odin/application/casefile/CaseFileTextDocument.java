package com.e2d.odin.application.casefile;

import com.e2d.odin.domain.context.DocumentContext;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.shared.DocumentId;
import java.util.Optional;

/** Un documento con páginas en la tanda: si está sustituido (y por cuál) y su contexto, una vez por documento. */
public record CaseFileTextDocument(DocumentId documentId, boolean superseded, Optional<DocumentRef> replacement,
        DocumentContext context) {
}
