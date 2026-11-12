package com.e2d.odin.application.document;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;

public record UploadOutcome(DocumentEntry entry, boolean duplicate) {

    public Document document() {
        return entry.document();
    }
}
