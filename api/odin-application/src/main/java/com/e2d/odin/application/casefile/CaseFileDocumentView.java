package com.e2d.odin.application.casefile;

import com.e2d.odin.application.document.ScanLinks;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;

/** Un documento del expediente con sus páginas, quién lo añadió y cuándo, y sus copias escaneadas. */
public record CaseFileDocumentView(DocumentEntry document, int pageCount, UserId addedBy, Instant addedAt,
        ScanLinks scanLinks) {
}
