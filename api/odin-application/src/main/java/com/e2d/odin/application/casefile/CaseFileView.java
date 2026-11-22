package com.e2d.odin.application.casefile;

import com.e2d.odin.domain.context.DocumentContext;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Map;

/** {@code names}: el nombre de quien subió o añadió cada documento, si sigue constando. */
public record CaseFileView(DocumentEntry caseDocument, int casePageCount, List<CaseFileDocumentView> documents,
        int pendingDocuments, int unreadableDocuments, Map<UserId, String> names, DocumentContext caseContext) {

    public CaseFileView(DocumentEntry caseDocument, int casePageCount, List<CaseFileDocumentView> documents,
            int pendingDocuments, int unreadableDocuments, Map<UserId, String> names) {
        this(caseDocument, casePageCount, documents, pendingDocuments, unreadableDocuments, names,
                DocumentContext.NONE);
    }
}
