package com.e2d.odin.application.search;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentPage;

public record PageView(DocumentEntry entry, DocumentPage page) {

    public Document document() {
        return entry.document();
    }
}
