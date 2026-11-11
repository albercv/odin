package com.e2d.odin.domain.document;

import com.e2d.odin.domain.shared.DocumentId;

public record DocumentPage(DocumentId documentId, int number, String text, String readerName, String readerVersion) {
}
