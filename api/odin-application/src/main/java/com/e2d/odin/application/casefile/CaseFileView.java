package com.e2d.odin.application.casefile;

import com.e2d.odin.domain.document.DocumentEntry;
import java.util.List;

public record CaseFileView(DocumentEntry caseDocument, List<CaseFileDocumentView> documents) {
}
