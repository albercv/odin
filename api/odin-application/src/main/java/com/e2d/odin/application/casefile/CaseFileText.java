package com.e2d.odin.application.casefile;

import java.util.List;
import java.util.Optional;

/** Una tanda del texto del expediente y cuánto lleva leído de él. */
public record CaseFileText(List<CaseFileTextPage> pages, Optional<String> nextCursor, boolean complete,
        int documentsRead, int documentsTotal, int pagesRead, int pagesTotal,
        int pendingDocuments, int unreadableDocuments) {
}
