package com.e2d.odin.application.citation;

import com.e2d.odin.application.port.DocumentLinkRepository;
import com.e2d.odin.application.port.DocumentLinkRepository.Pending;
import com.e2d.odin.domain.citation.DocumentCitation;
import com.e2d.odin.domain.citation.LinkExtractor;
import com.e2d.odin.domain.citation.OwnIdentifier;
import com.e2d.odin.domain.document.DocumentPage;
import java.time.Clock;
import java.util.List;

/** Vínculos por el identificador que se cita: sin IA y sin red. Sirve a los documentos nuevos y a los ya instalados. */
public class LinkDocuments {

    private final DocumentLinkRepository links;
    private final Clock clock;
    private final int batchSize;

    public LinkDocuments(DocumentLinkRepository links, Clock clock, int batchSize) {
        this.links = links;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /** Analiza un lote de documentos pendientes y dice cuántos fueron; 0 cuando no queda ninguno. */
    public int processBatch() {
        List<Pending> batch = links.pending(LinkExtractor.VERSION, batchSize);
        batch.forEach(this::analyze);
        return batch.size();
    }

    private void analyze(Pending document) {
        List<DocumentPage> pages = links.pagesOf(document);
        List<OwnIdentifier> own = LinkExtractor.ownIdentifiers(pages);
        List<DocumentCitation> citations = LinkExtractor.citations(pages, own);
        links.save(document, LinkExtractor.VERSION, own, citations, clock.instant());
    }
}
