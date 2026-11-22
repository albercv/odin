package com.e2d.odin.application.port;

import com.e2d.odin.domain.citation.DocumentCitation;
import com.e2d.odin.domain.citation.OwnIdentifier;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import java.time.Instant;
import java.util.List;

public interface DocumentLinkRepository {

    record Pending(CompanyId companyId, DocumentId documentId) {
    }

    /** Documentos listables y leídos, de cualquier empresa, que no se analizaron con esta versión del extractor. */
    List<Pending> pending(String extractorVersion, int limit);

    List<DocumentPage> pagesOf(Pending document);

    /**
     * En la transacción de su empresa: sustituye los identificadores y las citas detectadas del documento, marca el
     * documento como analizado y vuelve a resolver las citas de la empresa. No marca ninguna sustitución.
     */
    void save(Pending document, String extractorVersion, List<OwnIdentifier> identifiers,
            List<DocumentCitation> citations, Instant now);
}
