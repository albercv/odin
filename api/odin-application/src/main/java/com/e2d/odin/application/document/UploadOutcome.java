package com.e2d.odin.application.document;

import com.e2d.odin.domain.document.Document;

public record UploadOutcome(Document document, boolean duplicate) {
}
