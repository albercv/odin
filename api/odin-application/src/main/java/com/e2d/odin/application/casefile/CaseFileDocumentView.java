package com.e2d.odin.application.casefile;

import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;

/** Un documento del expediente con sus páginas y quién lo añadió y cuándo. */
public record CaseFileDocumentView(DocumentEntry document, int pageCount, UserId addedBy, Instant addedAt) {
}
