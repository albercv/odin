package com.e2d.odin.bootstrap;

import com.e2d.odin.application.citation.LinkDocuments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Carril de los vínculos entre documentos: comparte el hilo de programación y no se solapa consigo mismo. */
@Component
public class LinkDocumentsScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(LinkDocumentsScheduler.class);

    private final LinkDocuments link;

    public LinkDocumentsScheduler(LinkDocuments link) {
        this.link = link;
    }

    @Scheduled(fixedDelayString = "${odin.links.poll-interval:PT5S}")
    void run() {
        try {
            while (link.processBatch() > 0) {
                LOG.debug("Lote de documentos analizado para vínculos");
            }
        } catch (RuntimeException e) {
            LOG.error("Fallo en el carril de los vínculos entre documentos", e);
        }
    }
}
