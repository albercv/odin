package com.e2d.odin.domain.context;

import com.e2d.odin.domain.shared.DocumentId;
import java.util.Optional;

/** Una versión de la cadena de sustituciones; {@code match} solo lo rellena la búsqueda. */
public record DocumentVersion(DocumentId documentId, String title, boolean current, Optional<PageText> link,
        Optional<PageText> heading, Optional<PageText> match) {

    public DocumentVersion withMatch(Optional<PageText> found) {
        return new DocumentVersion(documentId, title, current, link, heading, found);
    }
}
