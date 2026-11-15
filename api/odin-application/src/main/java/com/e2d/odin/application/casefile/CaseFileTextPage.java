package com.e2d.odin.application.casefile;

import com.e2d.odin.domain.shared.DocumentId;

/** Una página del expediente; {@code unread} si el lector no sacó texto de ella. */
public record CaseFileTextPage(DocumentId documentId, String title, int pageNumber, String text, boolean unread) {
}
