package com.e2d.odin.application.casefile;

import com.e2d.odin.application.document.ScanLinks;
import com.e2d.odin.domain.context.DocumentContext;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;

/** Un documento del expediente con sus páginas, quién lo añadió y cuándo, sus copias escaneadas y su contexto. */
public record CaseFileDocumentView(DocumentEntry document, int pageCount, UserId addedBy, Instant addedAt,
        ScanLinks scanLinks, DocumentContext context) {

    public CaseFileDocumentView(DocumentEntry document, int pageCount, UserId addedBy, Instant addedAt,
            ScanLinks scanLinks) {
        this(document, pageCount, addedBy, addedAt, scanLinks, DocumentContext.NONE);
    }
}
