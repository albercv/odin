package com.e2d.odin.application.search;

import com.e2d.odin.domain.shared.DocumentId;

public record CitationCheck(DocumentId documentId, int pageNumber, String quote) {
}
